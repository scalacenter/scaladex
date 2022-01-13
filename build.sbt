import ScalaJSHelper._
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
    organization := "ch.epfl.scala",
    version := s"0.2.0+${githash()}"
  )
)

lazy val loggingSettings =
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
  )

val amm = inputKey[Unit]("Start Ammonite REPL")
lazy val ammoniteSettings = Def.settings(
  amm := (Test / run).evaluated,
  amm / aggregate := false,
  libraryDependencies += ("com.lihaoyi" % "ammonite" % "2.3.8-65-0f0d597f" % Test).cross(CrossVersion.full),
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

lazy val scalacOptionsSettings = Def.settings(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Wunused:imports"
  )
)

lazy val scaladex = project
  .in(file("."))
  .aggregate(webclient, data, core.jvm, core.js, infra, server, template)
  .settings(Deployment(data, server))

lazy val template = project
  .settings(
    scalacOptionsSettings,
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "com.github.nscala-time" %% "nscala-time" % V.nscalaTimeVersion,
      "com.typesafe" % "config" % "1.4.0",
      "com.typesafe.akka" %% "akka-http-core" % V.akkaHttpVersion,
      "org.scalatest" %% "scalatest" % V.scalatest % Test
    )
  )
  .dependsOn(core.jvm)
  .enablePlugins(SbtTwirl)

lazy val infra = project
  .in(file("infra"))
  .configs(IntegrationTest)
  .settings(
    scalacOptionsSettings,
    loggingSettings,
    libraryDependencies ++= Seq(
      "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % V.elastic4sVersion,
      "org.json4s" %% "json4s-native" % "3.6.9",
      "org.typelevel" %% "jawn-json4s" % "1.0.0",
      "org.flywaydb" % "flyway-core" % "7.11.0", // for database migration
      "com.typesafe.akka" %% "akka-stream" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-http" % V.akkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-circe" % "1.33.0",
      "io.get-coursier" %% "coursier" % "2.0.16",
      "org.tpolecat" %% "doobie-scalatest" % V.doobieVersion % Test,
      "org.scalatest" %% "scalatest" % V.scalatest % "test,it"
    ) ++ Seq(
      "org.tpolecat" %% "doobie-core",
      "org.tpolecat" %% "doobie-h2",
      "org.tpolecat" %% "doobie-postgres",
      "org.tpolecat" %% "doobie-hikari"
    ).map(_ % V.doobieVersion) ++ Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % V.circeVersion),
    Elasticsearch.settings(defaultPort = 9200),
    inConfig(Compile)(
      Postgres.settings(defaultPort = 5432, database = "scaladex")
    ),
    javaOptions ++= {
      val base = (ThisBuild / baseDirectory).value
      val index = base / "../scaladex-small-index/"
      val credentials = base / "../scaladex-credentials"
      val contrib = base / "../scaladex-contrib"
      Seq(
        s"-Dscaladex.filesystem.credentials=$credentials",
        s"-Dscaladex.filesystem.index=$index",
        s"-Dscaladex.filesystem.contrib=$contrib"
      )
    },
    Compile / run / javaOptions ++= {
      val elasticsearchPort = startElasticsearch.value
      val postgresPort = (Compile / startPostgres).value
      Seq(
        s"-Dscaladex.database.port=$postgresPort",
        s"-Dscaladex.elasticsearch.port=$elasticsearchPort"
      )
    },
    inConfig(Test)(
      Postgres.settings(defaultPort = 5432, database = "scaladex-test")
    ),
    Test / javaOptions ++= {
      val elasticsearchPort = startElasticsearch.value
      val postgresPort = (Test / startPostgres).value
      Seq(
        s"-Dscaladex.database.port=$postgresPort",
        s"-Dscaladex.database.name=scaladex-test",
        s"-Dscaladex.elasticsearch.index=scaladex-test",
        s"-Dscaladex.elasticsearch.port=$elasticsearchPort"
      )
    },
    Test / fork := true,
    // testing the database requests need to delete and create the tables,
    // which can fail if many tests are running in parallel
    Test / parallelExecution := false,
    Defaults.itSettings,
    IntegrationTest / fork := true,
    IntegrationTest / javaOptions ++= (Test / javaOptions).value
  )
  .dependsOn(core.jvm % "compile->compile;test->test;it->test")

lazy val webclient = project
  .settings(
    scalacOptionsSettings,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.8.6",
      "be.doeraene" %%% "scalajs-jquery" % "1.0.0",
      "org.endpoints4s" %%% "xhr-client" % "3.1.0"
    )
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(core.js)

lazy val server = project
  .configs(IntegrationTest)
  .settings(
    scalacOptionsSettings,
    loggingSettings,
    ammoniteSettings,
    packageScalaJS(webclient),
    javaOptions ++= Seq(
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % V.playJsonVersion,
      "org.scalatest" %% "scalatest" % V.scalatest % "test,it",
      "com.typesafe.akka" %% "akka-testkit" % V.akkaVersion % "test,it",
      "com.typesafe.akka" %% "akka-slf4j" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % V.akkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % V.akkaHttpVersion % Test,
      "ch.megard" %% "akka-http-cors" % "0.4.3",
      "com.softwaremill.akka-http-session" %% "core" % "0.5.11",
      "com.typesafe.akka" %% "akka-http" % V.akkaHttpVersion,
      "org.endpoints4s" %% "akka-http-server" % "5.1.0",
      "org.webjars" % "bootstrap-sass" % "3.4.1",
      "org.webjars" % "bootstrap-switch" % "3.3.2",
      "org.webjars" % "bootstrap-select" % "1.13.18",
      "org.webjars.bower" % "font-awesome" % "4.6.3",
      "org.webjars" % "jquery" % "3.6.0",
      "org.webjars.bower" % "select2" % "4.0.3"
    ),
    Compile / unmanagedResourceDirectories += (Assets / WebKeys.public).value,
    Compile / resourceGenerators += Def.task(
      Seq((Assets / WebKeys.assets).value)
    ),
    fork := true,
    Compile / run / javaOptions ++= (infra / Compile / run / javaOptions).value,
    Test / javaOptions ++= (infra / javaOptions).value,
    Defaults.itSettings,
    IntegrationTest / javaOptions ++= (infra / Compile / run / javaOptions).value
  )
  .dependsOn(template, data, infra, core.jvm % "compile->compile;test->test")
  .enablePlugins(SbtSassify, JavaServerAppPackaging)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("core"))
  .settings(
    scalacOptionsSettings,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "fastparse" % "2.3.0",
      "io.github.cquiroz" %%% "scala-java-time" % "2.2.2",
      "com.typesafe.play" %%% "play-json" % V.playJsonVersion,
      "org.endpoints4s" %%% "algebra" % "1.5.0",
      "org.scalatest" %%% "scalatest" % V.scalatest % Test
    ) ++ Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % V.circeVersion),
    buildInfoPackage := "build.info",
    buildInfoKeys := Seq[BuildInfoKey](ThisBuild / baseDirectory)
  )
  .enablePlugins(BuildInfoPlugin)

lazy val data = project
  .settings(
    scalacOptionsSettings,
    ammoniteSettings,
    loggingSettings,
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0",
      "com.github.nscala-time" %% "nscala-time" % V.nscalaTimeVersion,
      "com.typesafe.akka" %% "akka-stream" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % V.akkaVersion,
      "me.tongfei" % "progressbar" % "0.5.5",
      "org.apache.maven" % "maven-model-builder" % "3.3.9",
      "org.jsoup" % "jsoup" % "1.10.1",
      "com.typesafe.play" %% "play-ahc-ws" % "2.8.2",
      "org.apache.ivy" % "ivy" % "2.4.0",
      "com.typesafe.akka" %% "akka-http" % V.akkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-json4s" % "1.29.1",
      "org.json4s" %% "json4s-native" % "3.5.5",
      "org.scalatest" %% "scalatest" % V.scalatest % Test
    ),
    Compile / run / javaOptions ++= (infra / Compile / run / javaOptions).value,
    Test / javaOptions ++= (infra / javaOptions).value
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(core.jvm % "compile->compile;test->test", infra)

lazy val V = new {
  val doobieVersion = "0.13.4"
  val playJsonVersion = "2.9.0"
  val akkaVersion = "2.6.15"
  val akkaHttpVersion = "10.2.6"
  val elastic4sVersion = "7.10.2"
  val log4jVersion = "2.17.0"
  val nscalaTimeVersion = "2.24.0"
  val scalatest = "3.2.9"
  val circeVersion = "0.14.1"
}
