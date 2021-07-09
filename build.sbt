import ScalaJSHelper._
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import Deployment.githash

inThisBuild(
  List(
    scalaVersion := "2.13.5",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixScalaBinaryVersion := "2.13",
    scalafixDependencies ++= List(
      "com.github.liancheng" %% "organize-imports" % "0.4.4"
    ),
    organization := "ch.epfl.scala.index",
    version := s"0.2.0+${githash()}"
  )
)

lazy val logging =
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    "com.getsentry.raven" % "raven-logback" % "8.0.3"
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
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Wunused:imports"
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
) ++
  addCommandAlias("start", "reStart") ++ logging ++ ammoniteSettings

enablePlugins(Elasticsearch)

lazy val scaladex = project
  .in(file("."))
  .aggregate(
    client,
    data,
    core,
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
      "com.github.nscala-time" %% "nscala-time" % V.nscalaTimeVersion,
      "com.typesafe" % "config" % "1.4.0",
      "com.typesafe.akka" %% "akka-http-core" % V.akkaHttpVersion
    )
  )
  .dependsOn(core)
  .enablePlugins(SbtTwirl)

lazy val infra = project
  .in(file("infra"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % V.elastic4sVersion,
      "org.json4s" %% "json4s-native" % "3.6.9",
      "org.typelevel" %% "jawn-json4s" % "1.0.0",
      "org.flywaydb" % "flyway-core" % "7.11.0" // for database migration
    ) ++ Seq(
      "org.tpolecat" %% "doobie-core",
      "org.tpolecat" %% "doobie-h2",
      "org.tpolecat" %% "doobie-postgres",
      "org.tpolecat" %% "doobie-hikari"
    ).map(_ % V.doobieVersion)
  )
  .dependsOn(core)

lazy val api = crossProject(JSPlatform, JVMPlatform)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % V.playJsonVersion
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
      "com.typesafe.play" %%% "play-json" % V.playJsonVersion,
      "com.typesafe.akka" %% "akka-testkit" % V.akkaVersion % Test,
      "com.typesafe.akka" %% "akka-slf4j" % V.akkaVersion,
      "ch.megard" %% "akka-http-cors" % "0.4.3",
      "com.softwaremill.akka-http-session" %% "core" % "0.5.11",
      "com.typesafe.akka" %% "akka-http" % V.akkaHttpVersion,
      "org.webjars.bower" % "bootstrap-sass" % "3.3.6",
      "org.webjars.bower" % "bootstrap-switch" % "3.3.2",
      "org.webjars.bower" % "bootstrap-select" % "1.10.0",
      "org.webjars.bower" % "font-awesome" % "4.6.3",
      "org.webjars.bower" % "jQuery" % "2.2.4",
      "org.webjars.bower" % "raven-js" % "3.11.0",
      "org.webjars.bower" % "select2" % "4.0.3",
      "org.apache.logging.log4j" % "log4j-core" % V.log4jVersion % Runtime
    ),
    Universal / packageBin := (Universal / packageBin)
      .dependsOn(Assets / WebKeys.assets)
      .value,
    Test / test := (Test / test).dependsOn(startElasticsearch).value,
    reStart := reStart
      .dependsOn(startElasticsearch, Assets / WebKeys.assets)
      .evaluated,
    Compile / run := (Compile / run).dependsOn(startElasticsearch).evaluated,
    Compile / unmanagedResourceDirectories += (Assets / WebKeys.public).value,
    reStart / javaOptions ++= Seq("-Xmx4g")
  )
  .dependsOn(template, data, infra, api.jvm)
  .enablePlugins(SbtSassify, JavaServerAppPackaging)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies += "com.lihaoyi" %% "fastparse" % "2.3.0"
  )

lazy val data = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0",
      "com.github.nscala-time" %% "nscala-time" % V.nscalaTimeVersion,
      "com.typesafe.akka" %% "akka-stream" % V.akkaVersion,
      "me.tongfei" % "progressbar" % "0.5.5",
      "org.apache.maven" % "maven-model-builder" % "3.3.9",
      "org.jsoup" % "jsoup" % "1.10.1",
      "com.typesafe.play" %% "play-ahc-ws" % "2.8.2",
      "org.apache.ivy" % "ivy" % "2.4.0",
      "com.typesafe.akka" %% "akka-http" % V.akkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-json4s" % "1.29.1",
      "org.json4s" %% "json4s-native" % "3.5.5",
      "org.apache.logging.log4j" % "log4j-core" % V.log4jVersion % Runtime
    ),
    buildInfoPackage := "build.info",
    buildInfoKeys := Seq[BuildInfoKey](ThisBuild / baseDirectory),
    reStart := reStart.dependsOn(startElasticsearch).evaluated,
    Compile / run := (Compile / run).dependsOn(startElasticsearch).evaluated,
    reStart / javaOptions ++= Seq("-Xmx4g")
  )
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(core, infra)

lazy val V = new {
  val doobieVersion = "0.13.4"
  val playJsonVersion = "2.9.0"
  val akkaVersion = "2.6.5"
  val akkaHttpVersion = "10.1.12"
  val elastic4sVersion = "7.10.2"
  val log4jVersion = "2.13.3"
  val nscalaTimeVersion = "2.24.0"
}
