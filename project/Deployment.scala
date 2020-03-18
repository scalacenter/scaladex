import sbt._
import Keys._

import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.config.ConfigFactory

import java.nio.file._
import java.nio.file.attribute._

import scala.sys.process._

object Deployment {
  def apply(data: Project, server: Project): Seq[Def.Setting[_]] = Seq(
    deployServer := deployTask(server, prodUserName, prodPort).value,
    deployIndex := indexTask(data, prodUserName).value,
    deployDevServer := deployTask(server, devUserName, devPort).value,
    deployDevIndex := indexTask(data, devUserName).value
  )

  def deployTask(server: Project,
                 userName: String,
                 port: Int): Def.Initialize[Task[Unit]] = Def.task {
    val serverZip = (packageBin in (server, Universal)).value.toPath
    val deployment = deploymentTask(userName).value
    deployment.deploy(serverZip, port)
  }

  def indexTask(data: Project, userName: String): Def.Initialize[Task[Unit]] =
    Def.task {
      val dataZip = (packageBin in (data, Universal)).value.toPath
      val deployment = deploymentTask(userName).value
      deployment.index(dataZip)
    }

  private def deploymentTask(
      userName: String
  ): Def.Initialize[Task[Deployment]] =
    Def.task {
      new Deployment(
        rootFolder = (baseDirectory in ThisBuild).value,
        logger = streams.value.log,
        userName = userName,
        version = version.value
      )
    }

  def githash(): String = {
    if (!sys.env.contains("CI")) {
      val isDirty = Process("git diff-files --quiet").! == 1
      val indexState =
        if (isDirty) "-dirty"
        else ""

      Process("git rev-parse --verify HEAD").lineStream
        .mkString("") + indexState
    } else "CI"
  }

  private val deployServer = taskKey[Unit]("Deploy the server")
  private val deployIndex = taskKey[Unit]("Run index pipeline")

  private val deployDevServer = taskKey[Unit]("Deploy the dev server")
  private val deployDevIndex = taskKey[Unit]("Run dev index pipeline")

  private val devUserName = "devscaladex"
  private val prodUserName = "scaladex"

  private val devPort = 8082
  private val prodPort = 8080
}

class Deployment(rootFolder: File,
                 logger: Logger,
                 userName: String,
                 version: String) {

  def deploy(serverZip: Path, port: Int): Unit = {
    logger.info("Generate server script")

    val serverScript = Files.createTempDirectory("server").resolve("server.sh")

    val serverZipFileName = serverZip.getFileName

    val sentryDsn = getSentryDsn

    val scriptContent =
      s"""|#!/usr/bin/env bash
          |
          |whoami
          |kill `cat SERVER-PID`
          |
          |rm /home/$userName/server/server-*
          |unzip -d /home/$userName/server /home/$userName/$serverZipFileName
          |rm -rf /home/$userName/server/current
          |mkdir /home/$userName/server/current
          |mv /home/$userName/server/server-*/* /home/$userName/server/current
          |rmdir /home/$userName/server/server-*
          |
          |nohup /home/$userName/server/current/bin/server \\
          |  -J-Xmx4g \\
          |  -Dlogback.output-file=server.log \\
          |  -Dlogback.configurationFile=/home/$userName/scaladex-credentials/logback.xml \\
          |  -Dconfig.file=/home/$userName/scaladex-credentials/application.conf \\
          |  -Dsentry.dsn=$sentryDsn \\
          |  -Dsentry.release=$version \\
          |  $port \\
          |  /home/$userName/scaladex-contrib \\
          |  /home/$userName/scaladex-index \\
          |  /home/$userName/scaladex-credentials \\
          |  &>/dev/null &
          |""".stripMargin

    Files.write(serverScript, scriptContent.getBytes)
    Files.setPosixFilePermissions(serverScript, executablePermissions)

    logger.info("Deploy server task")

    rsync(serverZip)
    rsync(serverScript)

    val serverScriptFileName = serverScript.getFileName
    val uri = userName + "@" + serverHostname
    Process(s"ssh $uri ./$serverScriptFileName") ! logger
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

    val sentryDsn = getSentryDsn

    val scriptContent =
      s"""|#!/usr/bin/env bash
          |
          |if [ ! -f DATA-PID ]; then
          |  whoami
          |
          |$cloneAllIfAbsent
          |
          |  rm /home/$userName/data/data-*
          |  unzip -d /home/$userName/data /home/$userName/$dataZipFileName
          |  rm -rf /home/$userName/data/current
          |  mkdir /home/$userName/data/current
          |  mv /home/$userName/data/data-*/* /home/$userName/data/current
          |  rmdir /home/$userName/data/data-*
          |
          |  nohup /home/$userName/data/current/bin/data \\
          |    -J-Xmx6g \\
          |    -Dlogback.output-file=data.log \\
          |    -Dlogback.configurationFile=/home/$userName/scaladex-credentials/logback.xml \\
          |    -Dconfig.file=/home/$userName/scaladex-credentials/application.conf \\
          |    -Dsentry.dsn=$sentryDsn \\
          |    -Dsentry.release=$version \\
          |    all \\
          |    /home/$userName/scaladex-contrib \\
          |    /home/$userName/scaladex-index \\
          |    /home/$userName/scaladex-credentials \\
          |    &>/dev/null &
          |fi
          |
          |# list -> download -> parent -> sbt -> github -> elastic
          |# updateClaims
          |""".stripMargin

    Files.write(dataScript, scriptContent.getBytes)
    Files.setPosixFilePermissions(dataScript, executablePermissions)

    logger.info("Deploy indexing task")

    rsync(dataZip)
    rsync(dataScript)
  }

  private def rsync(file: Path): Unit = {
    val uri = userName + "@" + serverHostname
    val fileName = file.getFileName
    Process(s"rsync -av --progress $file $uri:$fileName") ! logger
  }

  private val executablePermissions =
    PosixFilePermissions.fromString("rwxr-xr-x")

  private val serverHostname = "index.scala-lang.org"

  private val getSentryDsn: String = {
    val scaladexCredentials = "scaladex-credentials"

    val secretFolder = rootFolder / ".." / scaladexCredentials

    if (Files.exists(secretFolder.toPath)) {
      Process("git pull origin master", secretFolder)
    } else {
      Process(
        s"git clone git@github.com:scaladex/$scaladexCredentials.git $secretFolder"
      )
    }

    val secretConfig = (secretFolder / "application.conf").toPath
    val config = ConfigFactory.parseFile(secretConfig.toFile)
    val scaladexConfig = config.getConfig("org.scala_lang.index")
    scaladexConfig.getString("sentry.dsn")
  }
}
