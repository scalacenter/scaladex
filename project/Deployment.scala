import sbt._
import Keys._

import com.typesafe.sbt.SbtNativePackager.Universal

import java.nio.file._
import java.nio.file.attribute._

import System.{lineSeparator => nl}

object Deployment {
  val deployServer = taskKey[Unit]("Deploy the server")
  val deployIndex = taskKey[Unit]("Run index pipeline")

  def apply(data: Project, server: Project): Seq[Def.Setting[_]] = Seq(
    deployServer := deployTask(server).value,
    deployIndex := indexTask(data).value
  )

  def deployTask(server: Project): Def.Initialize[Task[Unit]] = Def.task {
    val serverZip = (packageBin in (server, Universal)).value.toPath

    new Deployment(
      logger = streams.value.log
    ).deploy(serverZip)
  }

  def indexTask(data: Project): Def.Initialize[Task[Unit]] = Def.task {
    val dataZip = (packageBin in (data, Universal)).value.toPath

    new Deployment(
      logger = streams.value.log
    ).index(dataZip)
  }

  def githash(): String = {
    import sys.process._
    if (!sys.env.contains("CI")) {
      val isDirty = Process("git diff-files --quiet").! == 1
      val indexState =
        if (isDirty) "-dirty"
        else ""

      Process("git rev-parse --verify HEAD").lines.mkString("") + indexState
    } else "CI"
  }
}

class Deployment(logger: Logger) {

  def deploy(serverZip: Path): Unit = {
    logger.info("Generate server script")

    val serverScript = Files.createTempDirectory("server").resolve("server.sh")

    val serverZipFileName = serverZip.getFileName

    val scriptContent =
      s"""|#!/usr/bin/env bash
          |
          |if [ ! -f SERVER-PID ]; then
          |  whoami
          |
          |  rm server/server-*
          |  unzip -d server $serverZipFileName
          |  rm -rf server/current
          |  mkdir server/current
          |  mv server/server-*/* server/current
          |  rmdir server/server-*
          |
          |  nohup server/current/bin/server \\
          |    -J-Xmx2g \\
          |    -Dconfig.file=/home/$userName/scaladex-credentials/application.conf \\
          |    8081 \\
          |    /home/$userName/scaladex-contrib \\
          |    /home/$userName/scaladex-index \\
          |    /home/$userName/scaladex-credentials \\
          |    &>/home/$userName/server.log &
          |fi
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
      ).map(cloneIfAbsent).mkString(nl)

    val scriptContent =
      s"""|#!/usr/bin/env bash
          |
          |if [ ! -f DATA-PID ]; then
          |  whoami
          |
          |$cloneAllIfAbsent
          |
          |  rm data/data-*
          |  unzip -d data $dataZipFileName
          |  rm -rf data/current
          |  mkdir data/current
          |  mv data/data-*/* data/current
          |  rmdir data/data-*
          |
          |  nohup data/current/bin/data \\
          |    -J-Xmx3g \\
          |    -Dconfig.file=/home/$userName/scaladex-credentials/application.conf \\
          |    all \\
          |    /home/$userName/scaladex-contrib \\
          |    /home/$userName/scaladex-index \\
          |    /home/$userName/scaladex-credentials \\
          |    &>/home/$userName/data.log &
          |fi
          |""".stripMargin

    Files.write(dataScript, scriptContent.getBytes)
    Files.setPosixFilePermissions(dataScript, executablePermissions)

    logger.info("Deploy indexing task")

    rsync(dataZip)
    rsync(dataScript)

    val dataScriptFileName = dataScript.getFileName
    val uri = userName + "@" + serverHostname
    Process(s"ssh $uri ./$dataScriptFileName") ! logger
  }

  private def rsync(file: Path): Unit = {
    val uri = userName + "@" + serverHostname
    val fileName = file.getFileName
    Process(s"rsync -av --progress $file $uri:$fileName") ! logger
  }

  private val executablePermissions =
    PosixFilePermissions.fromString("rwxr-xr-x")

  // private val userName = "devscaladex"
  private val userName = "scaladex"
  private val serverHostname = "index.scala-lang.org"
}
