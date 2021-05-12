import ScalaJSHelper._
import sbtcrossproject.CrossPlugin.autoImport.crossProject

import Deployment.githash

val playJsonVersion = "2.9.0"
val akkaVersion = "2.6.5"
val akkaHttpVersion = "10.1.12"
val elastic4sVersion = "7.10.2"
val log4jVersion = "2.13.3"
val nscalaTimeVersion = "2.24.0"

lazy val logging =
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    "com.getsentry.raven" % "raven-logback" % "8.0.3"
  )

lazy val baseSettings = Seq(
  organization := "ch.epfl.scala.index",
  version := s"0.2.0+${githash()}"
)

val amm = inputKey[Unit]("Start Ammonite REPL")
lazy val ammoniteSettings = Seq(
  amm := (Test / run).evaluated,
  amm / aggregate := false,
  libraryDependencies += "com.lihaoyi" % "ammonite" % "2.3.8-65-0f0d597f" % Test cross CrossVersion.full,
  Test / sourceGenerators += Def.task {
    val file = (Test / sourceManaged).value / "amm.scala"
    IO.write(
      file,
      """
        |object AmmoniteBridge extends App {
        |  ammonite.Main.main(args)
        |}
      """.stripMargin
    )
    Seq(file)
  }.taskValue
)

lazy val commonSettings = Seq(
  scalaVersion := "2.13.5",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings"
  ),
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.9" % Test,
  reStart / javaOptions ++= {
    val base = (ThisBuild / baseDirectory).value

    val devCredentials = base / "../scaladex-dev-credentials/application.conf"

    val addDevCredentials =
      if (devCredentials.exists) Seq(s"-Dconfig.file=$devCredentials")
      else Seq()

    addDevCredentials
  }
) ++ baseSettings ++
  addCommandAlias("start", "reStart") ++ logging ++ ammoniteSettings

enablePlugins(Elasticsearch)

lazy val scaladex = project
  .in(file("."))
  .aggregate(
    client,
    data,
    model,
    server,
    api.jvm,
    api.js,
    template
  )
  .settings(commonSettings)
  .settings(Deployment(data, server))

lazy val template = project
  .settings(commonSettings)
  .settings(
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "com.github.nscala-time" %% "nscala-time" % nscalaTimeVersion,
      "com.typesafe" % "config" % "1.4.0",
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
    )
  )
  .dependsOn(model)
  .enablePlugins(SbtTwirl)

lazy val search = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % elastic4sVersion,
      "org.json4s" %% "json4s-native" % "3.6.9",
      "org.typelevel" %% "jawn-json4s" % "1.0.0"
    )
  )
  .dependsOn(model)

lazy val api = crossProject(JSPlatform, JVMPlatform)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % playJsonVersion
    )
  )

lazy val client = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.8.6",
      "be.doeraene" %%% "scalajs-jquery" % "1.0.0"
    )
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(api.js)

lazy val server = project
  .settings(commonSettings)
  .settings(packageScalaJS(client))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % playJsonVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "ch.megard" %% "akka-http-cors" % "0.4.3",
      "com.softwaremill.akka-http-session" %% "core" % "0.5.11",
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "org.webjars.bower" % "bootstrap-sass" % "3.3.6",
      "org.webjars.bower" % "bootstrap-switch" % "3.3.2",
      "org.webjars.bower" % "bootstrap-select" % "1.10.0",
      "org.webjars.bower" % "font-awesome" % "4.6.3",
      "org.webjars.bower" % "jQuery" % "2.2.4",
      "org.webjars.bower" % "raven-js" % "3.11.0",
      "org.webjars.bower" % "select2" % "4.0.3",
      "org.apache.logging.log4j" % "log4j-core" % log4jVersion % Runtime
    ),
    Universal / packageBin := (Universal / packageBin)
      .dependsOn(Assets / WebKeys.assets)
      .value,
    Test / test := (Test / test).dependsOn(startElasticsearch).value,
    reStart := reStart.dependsOn(startElasticsearch).evaluated,
    Compile / run := (Compile / run).dependsOn(startElasticsearch).evaluated,
    Compile / unmanagedResourceDirectories += (Assets / WebKeys.public).value,
    reStart / javaOptions ++= Seq("-Xmx4g")
  )
  .dependsOn(template, data, search, api.jvm)
  .enablePlugins(SbtSassify, JavaServerAppPackaging)

lazy val model = project
  .settings(commonSettings)
  .settings(
    libraryDependencies += "com.lihaoyi" %% "fastparse" % "2.3.0"
  )

lazy val data = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0",
      "com.github.nscala-time" %% "nscala-time" % nscalaTimeVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "me.tongfei" % "progressbar" % "0.5.5",
      "org.apache.maven" % "maven-model-builder" % "3.3.9",
      "org.jsoup" % "jsoup" % "1.10.1",
      "com.typesafe.play" %% "play-ahc-ws" % "2.8.2",
      "org.apache.ivy" % "ivy" % "2.4.0",
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-json4s" % "1.29.1",
      "org.json4s" %% "json4s-native" % "3.5.5",
      "org.apache.logging.log4j" % "log4j-core" % log4jVersion % Runtime
    ),
    buildInfoPackage := "build.info",
    buildInfoKeys := Seq[BuildInfoKey](ThisBuild / baseDirectory),
    reStart := reStart.dependsOn(startElasticsearch).evaluated,
    Compile / run := (Compile / run).dependsOn(startElasticsearch).evaluated,
    reStart / javaOptions ++= Seq("-Xmx4g")
  )
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(model, search)
