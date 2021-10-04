import ScalaJSHelper._
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import Deployment.githash
import ESandPostgreSQLContainers.autoImport.startESandPostgreSQL

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
//    "-Xfatal-warnings",
    "-Wunused:imports"
  ),
  libraryDependencies += "org.scalatest" %% "scalatest" % V.scalatest % Test,
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

enablePlugins(ESandPostgreSQLContainers)

lazy val scaladex = project
  .in(file("."))
  .aggregate(
    client,
    data,
    core,
    infra,
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
    Test / test := (Test / test).dependsOn(startESandPostgreSQL).value,
    libraryDependencies ++= Seq(
      "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % V.elastic4sVersion,
      "org.json4s" %% "json4s-native" % "3.6.9",
      "org.typelevel" %% "jawn-json4s" % "1.0.0",
      "org.flywaydb" % "flyway-core" % "7.11.0", // for database migration
      "org.tpolecat" %% "doobie-scalatest" % V.doobieVersion % Test
    ) ++ Seq(
      "org.tpolecat" %% "doobie-core",
      "org.tpolecat" %% "doobie-h2",
      "org.tpolecat" %% "doobie-postgres",
      "org.tpolecat" %% "doobie-hikari"
    ).map(_ % V.doobieVersion)
      ++ Seq(
        "io.circe" %% "circe-core",
        "io.circe" %% "circe-generic",
        "io.circe" %% "circe-parser"
      ).map(_ % V.circeVersion),
    // testing the database requests need to delete and create the tables,
    // which can fail if many tests are running in parallel
    Test / parallelExecution := false
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
    javaOptions ++= Seq(
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % V.playJsonVersion,
      "com.typesafe.akka" %% "akka-testkit" % V.akkaVersion % Test,
      "com.typesafe.akka" %% "akka-slf4j" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % V.akkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % "10.2.6" % Test,
      "ch.megard" %% "akka-http-cors" % "0.4.3",
      "com.softwaremill.akka-http-session" %% "core" % "0.5.11",
      "com.typesafe.akka" %% "akka-http" % V.akkaHttpVersion,
      "org.webjars.bowergithub.twbs" % "bootstrap" % "5.1.1",
      "org.webjars.bowergithub.bttstrp" % "bootstrap-switch" % "3.3.4",
      "org.webjars.bowergithub.thdoan" % "bootstrap-select" % "1.6.4",
      "org.webjars.bowergithub.fortawesome" % "font-awesome" % "4.7.0",
      "org.webjars.bowergithub.jquery" % "jquery" % "3.6.0",
      "org.webjars.bowergithub.getsentry" % "raven-js" % "3.24.2",
      "org.webjars.bowergithub.select2" % "select2" % "4.0.13",
      "org.apache.logging.log4j" % "log4j-core" % V.log4jVersion % Runtime
    ),
    Universal / packageBin := (Universal / packageBin)
      .dependsOn(Assets / WebKeys.assets)
      .value,
    Test / test := (Test / test).dependsOn(startESandPostgreSQL).value,
    reStart := reStart
      .dependsOn(startESandPostgreSQL, Assets / WebKeys.assets)
      .evaluated,
    Compile / run / fork := true,
    Compile / run := (Compile / run)
      .dependsOn(startESandPostgreSQL, Assets / WebKeys.assets)
      .evaluated,
    Compile / unmanagedResourceDirectories += (Assets / WebKeys.public).value,
    reStart / javaOptions ++= Seq("-Xmx4g")
  )
  .dependsOn(template, data, infra, api.jvm)
  .enablePlugins(SbtSassify, JavaServerAppPackaging)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "fastparse" % "2.3.0",
      "joda-time" % "joda-time" % "2.10.10"
    ),
    buildInfoPackage := "build.info",
    buildInfoKeys := Seq[BuildInfoKey](ThisBuild / baseDirectory)
  )
  .enablePlugins(BuildInfoPlugin)

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
    reStart := reStart.dependsOn(startESandPostgreSQL).evaluated,
    Compile / run := (Compile / run).dependsOn(startESandPostgreSQL).evaluated,
    reStart / javaOptions ++= Seq("-Xmx4g")
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(core, infra)

lazy val V = new {
  val doobieVersion = "0.13.4"
  val playJsonVersion = "2.9.0"
  val akkaVersion = "2.6.5"
  val akkaHttpVersion = "10.1.12"
  val elastic4sVersion = "7.10.2"
  val log4jVersion = "2.13.3"
  val nscalaTimeVersion = "2.24.0"
  val scalatest = "3.2.9"
  val circeVersion = "0.14.1"
}
