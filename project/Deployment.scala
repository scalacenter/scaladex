import sbt._
import Keys._

import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.config.ConfigFactory

import java.nio.file._
import java.nio.file.attribute._

import scala.sys.process._

object Deployment {
  def apply(data: Project, server: Project): Seq[Def.Setting[?]] = Seq(
    deployServer := deployTask(server, prodUserName, prodHostname, Some(jumpHost)).value,
    deployIndex := indexTask(data, prodUserName, prodHostname, Some(jumpHost)).value,
    deployDevServer := deployTask(server, devUserName, devHostname, None).value,
    deployDevIndex := indexTask(data, devUserName, devHostname, None).value
  )

  def deployTask(
      server: Project,
      userName: String,
      hostname: String,
      jumpHost: Option[String]
  ): Def.Initialize[Task[Unit]] = Def.task {
    val serverZip = (server / Universal / packageBin).value.toPath
    val deployment = deploymentTask(userName, hostname, jumpHost).value
    deployment.deploy(serverZip)
  }

  def indexTask(
      data: Project,
      userName: String,
      hostname: String,
      jumpHost: Option[String]
  ): Def.Initialize[Task[Unit]] =
    Def.task {
      val dataZip = (data / Universal / packageBin).value.toPath
      val deployment = deploymentTask(userName, hostname, jumpHost).value
      deployment.index(dataZip)
    }

  private def deploymentTask(
      userName: String,
      hostname: String,
      jumpHost: Option[String]
  ): Def.Initialize[Task[Deployment]] =
    Def.task {
      new Deployment(
        rootFolder = (ThisBuild / baseDirectory).value,
        logger = streams.value.log,
        userName = userName,
        hostname = hostname,
        jumpHost = jumpHost,
        version = version.value
      )
    }

  def githash(): String =
    if (!sys.env.contains("CI")) {
      val isDirty = Process("git diff-files --quiet").! == 1
      val indexState =
        if (isDirty) "-dirty"
        else ""

      Process("git rev-parse --verify HEAD").lineStream
        .mkString("") + indexState
    } else "CI"

  private val deployServer = taskKey[Unit]("Deploy the server")
  private val deployIndex = taskKey[Unit]("Run index pipeline")

  private val deployDevServer = taskKey[Unit]("Deploy the dev server")
  private val deployDevIndex = taskKey[Unit]("Run dev index pipeline")

  private val devUserName = "devscaladex"
  private val prodUserName = "scaladex"

  private val devHostname = "alaska.epfl.ch"
  private val prodHostname = "icvm0032.epfl.ch"
  private val jumpHost = s"$devUserName@$devHostname"
}

class Deployment(
    rootFolder: File,
    logger: Logger,
    userName: String,
    hostname: String,
    jumpHost: Option[String],
    version: String
) {

  def deploy(serverZip: Path): Unit = {
    logger.info("Generate server script")

    val serverScript = Files.createTempDirectory("server").resolve("server.sh")

    val serverZipFileName = serverZip.getFileName

    val scriptContent =
      s"""|#!/usr/bin/env -S bash -l
          |
          |whoami
          |kill `cat SERVER-PID`
          |
          |rmdir /home/$userName/server/server-*
          |find /home/$userName/ -maxdepth 1 -type f -name 'server-*' -not -name '$serverZipFileName' -delete
          |unzip -d /home/$userName/server /home/$userName/$serverZipFileName
          |rm -rf /home/$userName/server/current
          |mkdir /home/$userName/server/current
          |mv /home/$userName/server/server-*/* /home/$userName/server/current
          |
          |nohup /home/$userName/server/current/bin/server \\
          |  -J-Xmx4g \\
          |  -Dcom.sun.management.jmxremote=true \\
          |  -Dcom.sun.management.jmxremote.ssl=false \\
          |  -Dcom.sun.management.jmxremote.authenticate=false \\
          |  -Dcom.sun.management.jmxremote.port=9999 \\
          |  -Djava.rmi.server.hostname=localhost \\
          |  -Dcom.sun.management.jmxremote.rmi.port=9998 \\
          |  -Dlogback.output-file=server.log \\
          |  -Dlogback.logs-dir=/home/$userName/server/logs \\
          |  -Dlogback.configurationFile=logback-prod.xml \\
          |  -Dconfig.file=/home/$userName/scaladex-credentials/application.conf \\
          |  &>/dev/null &
          |""".stripMargin

    Files.write(serverScript, scriptContent.getBytes)
    Files.setPosixFilePermissions(serverScript, executablePermissions)

    logger.info("Deploy server task")

    val serverScriptFileName = serverScript.getFileName
    val uri = userName + "@" + hostname

    jumpHost match {
      case Some(jump) =>
        // Two-phase deployment: first to jump host, then to target
        Process(Seq("ssh", jump, s"mkdir -p $jumpStagingDir")) ! logger
        Process(
          Seq("rsync", "-av", "--progress", serverZip.toString, s"$jump:$jumpStagingDir/$serverZipFileName")
        ) ! logger
        Process(
          Seq("rsync", "-av", "--progress", serverScript.toString, s"$jump:$jumpStagingDir/$serverScriptFileName")
        ) ! logger

        // From jump host: rsync staged files to target, execute the script, then remove the staging directory
        val remoteCommands = s"""
                                |rsync -av --progress ~/$jumpStagingDir/$serverZipFileName $uri:$serverZipFileName
                                |rsync -av --progress ~/$jumpStagingDir/$serverScriptFileName $uri:$serverScriptFileName
                                |ssh $uri ./$serverScriptFileName
                                |rm -rf ~/$jumpStagingDir
                                |""".stripMargin
        Process(Seq("ssh", jump, remoteCommands)) ! logger

      case None =>
        rsync(serverZip)
        rsync(serverScript)
        Process(Seq("ssh", uri, s"./$serverScriptFileName")) ! logger
    }
  }

  def index(dataZip: Path): Unit = {
    logger.info("Generate indexing script")

    val dataScript = Files.createTempDirectory("index").resolve("index.sh")

    val dataZipFileName = dataZip.getFileName

    def cloneIfAbsent(repo: String): String = {
      val repo0 = s"scaladex-$repo"

      s"""|  if [ ! -d "$repo0" ]; then
          |    git clone git@github.com:scalacenter/$repo0;
          |  fi""".stripMargin
    }

    val cloneAllIfAbsent =
      List(
        "credentials",
        "contrib",
        "index"
      ).map(cloneIfAbsent).mkString("\n")

    val scriptContent =
      s"""|#!/usr/bin/env -S bash -l
          |
          |if [ ! -f DATA-PID ]; then
          |  whoami
          |
          |$cloneAllIfAbsent
          |
          |  rmdir /home/$userName/data/data-*
          |  find /home/$userName/ -maxdepth 1 -type f -name 'data-*' -not -name '$dataZipFileName' -delete
          |  unzip -d /home/$userName/data /home/$userName/$dataZipFileName
          |  rm -rf /home/$userName/data/current
          |  mkdir /home/$userName/data/current
          |  mv /home/$userName/data/data-*/* /home/$userName/data/current
          |
          |  nohup /home/$userName/data/current/bin/data \\
          |    -J-Xmx2g \\
          |    -Dlogback.output-file=data.log \\
          |    -Dlogback.logs-dir=/home/$userName/data/logs \\
          |    -Dlogback.configurationFile=logback-prod.xml \\
          |    -Dconfig.file=/home/$userName/scaladex-credentials/application.conf \\
          |    init \\
          |    &>/dev/null &
          |fi
          |
          |# the old workflow was:
          |# list -> download -> parent -> sbt -> github -> seed
          |# updateClaims
          |""".stripMargin

    Files.write(dataScript, scriptContent.getBytes)
    Files.setPosixFilePermissions(dataScript, executablePermissions)

    logger.info("Deploy indexing task")

    jumpHost match {
      case Some(jump) =>
        rsyncViaJumpHost(dataZip, jump)
        rsyncViaJumpHost(dataScript, jump)
      case None =>
        rsync(dataZip)
        rsync(dataScript)
    }
  }

  private def rsync(file: Path): Unit = {
    val uri = userName + "@" + hostname
    val fileName = file.getFileName
    Process(Seq("rsync", "-av", "--progress", file.toString, s"$uri:$fileName")) ! logger
  }

  private def rsyncViaJumpHost(file: Path, jump: String): Unit = {
    val uri = userName + "@" + hostname
    val fileName = file.getFileName
    // First: local -> jump host
    Process(Seq("ssh", jump, s"mkdir -p $jumpStagingDir")) ! logger
    Process(Seq("rsync", "-av", "--progress", file.toString, s"$jump:$jumpStagingDir/$fileName")) ! logger
    val remoteCommands =
      s"rsync -av --progress ~/$jumpStagingDir/$fileName $uri:$fileName && rm -f ~/$jumpStagingDir/$fileName"
    Process(Seq("ssh", jump, remoteCommands)) ! logger
  }

  private val executablePermissions =
    PosixFilePermissions.fromString("rwxr-xr-x")

  private val jumpStagingDir = "scaladex-prod-deploy"
}
