import ScalaJSHelper._
import Deployment.githash

lazy val isCI: Boolean = System.getenv("CI") != null

// lazy val scala3 = "2.13.10"
lazy val scala3 = "3.4.0-RC1-bin-SNAPSHOT"
// lazy val scala3 = "3.3.1-RC1-bin-20230522-fe08f5f-NIGHTLY"

inThisBuild(
  List(
//    usePipelining := true,
    scalaVersion := scala3,
    crossScalaVersions := Seq(scala3),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixScalaBinaryVersion := "3",
    scalafixDependencies ++= List(
      "com.github.liancheng" %% "organize-imports" % "0.6.0"
    ),
    organization := "ch.epfl.scala",
    version := s"0.2.0+${githash()}"
  )
)

lazy val loggingSettings = Seq(
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.3.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  ),
  // Drop and replace commons-logging with slf4j
  libraryDependencies += "org.slf4j" % "jcl-over-slf4j" % "2.0.7",
  excludeDependencies += ExclusionRule("commons-logging", "commons-logging")
)

lazy val scalacOptionsSettings = Def.settings(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    // "-Ytasty-reader",
    "-Wunused:all",
  ) ++ { if (isCI) Some("-Xfatal-warnings") else None }
)

lazy val scaladex = project
  .in(file("."))
  .aggregate(webclient, data, core.jvm, core.js, infra, server, template, akkaHttpCirceShaded)
  .settings(Deployment(data, server))

lazy val template = project
  .in(file("modules/template"))
  .enablePlugins(SbtTwirl)
  .settings(
    libraryDependencies ~= { _.map(_.exclude("org.scala-lang.modules", "scala-xml_3")) },
  )
  .settings(
    scalacOptionsSettings,
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      ("org.scala-lang.modules" %% "scala-xml" % "2.1.0").cross(CrossVersion.for3Use2_13),
      ("com.github.nscala-time" %% "nscala-time" % V.nscalaTimeVersion).cross(CrossVersion.for3Use2_13),
      "com.typesafe" % "config" % "1.4.2",
      ("com.typesafe.akka" %% "akka-http-core" % V.akkaHttpVersion).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-stream" % V.akkaVersion % Provided).cross(CrossVersion.for3Use2_13),
      ("org.scalatest" %%% "scalatest" % V.scalatest % Test).cross(CrossVersion.for3Use2_13),
    )
  )
  .dependsOn(core.jvm % "compile->compile;test->test")

lazy val akkaHttpCirceShaded = project
  .in(file("modules/akka-http-circe-shaded"))
  .settings(
    scalacOptionsSettings,
    name := "akka-http-circe",
    libraryDependencies ++= Seq(
      ("com.typesafe.akka" %% "akka-http"     % V.akkaHttpVersion).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-stream" % V.akkaVersion % Provided).cross(CrossVersion.for3Use2_13),
      "io.circe"          %% "circe-core"  % V.circeVersion,
      "io.circe"          %% "circe-parser" % V.circeVersion,
      // "io.circe"          %% "circe-generic" % V.circeVersion % Test,
      // library.scalaTest    % Test,
    )
  )

lazy val infra = project
  .in(file("modules/infra"))
  .configs(IntegrationTest)
  .settings(
    scalacOptionsSettings,
    loggingSettings,
    libraryDependencies += "org.flywaydb" % "flyway-core" % "8.5.13", // for database migration
    libraryDependencies ++= (Seq(
      "io.get-coursier" %% "coursier" % "2.1.3" exclude("org.scala-lang.modules", "scala-collection-compat_2.13") exclude("org.scala-lang.modules", "scala-xml_2.13"),
      // "org.tpolecat" %% "doobie-scalatest" % V.doobieVersion % Test,
      // "org.scalatest" %% "scalatest" % V.scalatest % "test,it"
    )).map(_.cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= (Seq(
      ("com.typesafe.akka" %% "akka-stream" % V.akkaVersion).cross(CrossVersion.for3Use2_13),
      ("com.typesafe.akka" %% "akka-http" % V.akkaHttpVersion).cross(CrossVersion.for3Use2_13),
      ("com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % V.elastic4sVersion).cross(CrossVersion.for3Use2_13),
    ) ++ Seq(
      "org.tpolecat" %% "doobie-core",
      "org.tpolecat" %% "doobie-h2",
      "org.tpolecat" %% "doobie-postgres",
      "org.tpolecat" %% "doobie-hikari"
    ).map(_ % V.doobieVersion) ++ Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % V.circeVersion)),
    Elasticsearch.settings(defaultPort = 9200),
    inConfig(Compile)(
      Postgres.settings(defaultPort = 5432, database = "scaladex")
    ),
    javaOptions ++= {
      val base = (ThisBuild / baseDirectory).value
      val index = base / "small-index"
      val contrib = base / "contrib"
      Seq(
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
  .dependsOn(akkaHttpCirceShaded, core.jvm % "compile->compile;test->test;it->test")

lazy val webclient = project
  .in(file("modules/webclient"))
  .settings(
    scalacOptionsSettings,
    scalacOptions -= "-Wunused", // don't report unused params
    scalacOptions += "-Wunused:imports",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.12.0" exclude("com.lihaoyi", "geny_sjs1_3"),
      "org.endpoints4s" %%% "fetch-client" % "3.0.0"
    )
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(core.js)

lazy val server = project
  .in(file("modules/server"))
  .configs(IntegrationTest)
  .settings(
    scalacOptionsSettings,
    javaOptions ++= Seq(
      "-Dcom.sun.management.jmxremote.port=9999",
      "-Dcom.sun.management.jmxremote.authenticate=false",
      "-Dcom.sun.management.jmxremote.ssl=false"
    ),
    loggingSettings,
    packageScalaJS(webclient),
    javaOptions ++= Seq(
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044"
    ),
    libraryDependencies ++= Seq(
      "org.webjars" % "bootstrap-sass" % "3.4.1",
      "org.webjars" % "bootstrap-switch" % "3.3.4",
      "org.webjars" % "bootstrap-select" % "1.13.18",
      "org.webjars" % "chartjs" % "3.9.1",
      "org.webjars.npm" % "date-fns" % "2.30.0",
      "org.webjars.npm" % "chartjs-adapter-date-fns" % "2.0.0",
      "org.webjars" % "font-awesome" % "6.4.0",
      "org.webjars" % "jquery" % "3.6.3",
      "org.webjars.bower" % "select2" % "4.0.13"
    ) ++ Seq(
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",
      ("com.typesafe.play" %%% "play-json" % V.playJsonVersion).cross(CrossVersion.for3Use2_13),
      "org.scalatest" %% "scalatest" % V.scalatest % "test,it",
      "com.typesafe.akka" %% "akka-testkit" % V.akkaVersion % "test,it",
      "com.typesafe.akka" %% "akka-slf4j" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % V.akkaVersion % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % V.akkaHttpVersion % Test,
      "ch.megard" %% "akka-http-cors" % "1.2.0",
      "com.softwaremill.akka-http-session" %% "core" % "0.7.0",
      "com.typesafe.akka" %% "akka-http" % V.akkaHttpVersion,
      ("org.endpoints4s" %% "akka-http-server" % "7.1.0" exclude("com.lihaoyi", "upickle-core_2.13") exclude("org.scala-lang.modules", "scala-collection-compat_2.13") exclude("org.endpoints4s","algebra_2.13") exclude("org.endpoints4s","algebra-json-schema_2.13")),
    ).map(_.cross(CrossVersion.for3Use2_13)),
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
  .in(file("modules/core"))
  .settings(
    scalacOptionsSettings,
    libraryDependencies += "org.jsoup" % "jsoup" % "1.16.1",
    libraryDependencies += "com.lihaoyi" %%% "fastparse" % "3.0.1" exclude("com.lihaoyi", "geny_sjs1_3"),
    libraryDependencies ++= (Seq(
      "io.github.cquiroz" %%% "scala-java-time" % "2.4.0",
      ("com.typesafe.play" %%% "play-json" % V.playJsonVersion).cross(CrossVersion.for3Use2_13),
      "org.endpoints4s" %%% "algebra" % "1.9.0",
      "org.endpoints4s" %% "json-schema-playjson" % "1.9.0" % Test exclude("com.typesafe.play", "play-json_2.13"),
      ("org.scalatest" %%% "scalatest" % V.scalatest % Test).cross(CrossVersion.for3Use2_13),
    )),//.map(_.cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser"
    ).map(_ % V.circeVersion).map(_.cross(CrossVersion.for2_13Use3))
  )

lazy val data = project
  .in(file("modules/data"))
  .settings(
    scalacOptionsSettings,
    loggingSettings,
    libraryDependencies ++= Seq(
      "org.apache.maven" % "maven-model-builder" % "3.9.2",
      "org.jsoup" % "jsoup" % "1.16.1",
      "org.apache.ivy" % "ivy" % "2.5.1",
    ),
    libraryDependencies ++= Seq(
      "com.github.nscala-time" %% "nscala-time" % V.nscalaTimeVersion,
      "com.typesafe.akka" %% "akka-stream" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % V.akkaVersion,
      "com.typesafe.akka" %% "akka-http" % V.akkaHttpVersion,
      "org.json4s" %% "json4s-native" % V.json4s,
      "org.scalatest" %% "scalatest" % V.scalatest % Test
    ).map(_.cross(CrossVersion.for3Use2_13)),
    run / fork := true,
    Compile / run / javaOptions ++= (infra / Compile / run / javaOptions).value,
    Test / javaOptions ++= (infra / javaOptions).value
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(core.jvm % "compile->compile;test->test", infra)

lazy val V = new {
  val doobieVersion = "0.13.4"
  // val playJsonVersion = "2.10.0-RC8+2-e4ed8081+20230519-2101-SNAPSHOT"
  val playJsonVersion = "2.9.4"
  val akkaVersion = "2.6.18"
  val akkaHttpVersion = "10.2.10"
  val elastic4sVersion = "8.7.0"
  val nscalaTimeVersion = "2.32.0"
  val scalatest = "3.2.15"
  val circeVersion = "0.14.5"
  val json4s = "4.0.6"
}
