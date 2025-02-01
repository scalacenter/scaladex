import ScalaJSHelper._
import Deployment.githash

lazy val isCI: Boolean = System.getenv("CI") != null

inThisBuild(
  List(
    scalaVersion := "3.3.4",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    // scalafixScalaBinaryVersion := "3",
    organization := "ch.epfl.scala",
    version := s"0.2.0+${githash()}",
    excludeDependencies ++= Seq(
      // from Coursier
      ExclusionRule("org.scala-lang.modules", "scala-collection-compat_2.13"),
      ExclusionRule("org.scala-lang.modules", "scala-xml_2.13"),
      // from doobie-scalatest
      ExclusionRule("org.scalatest", "scalatest_2.13"),
      ExclusionRule("org.tpolecat", "doobie-core_2.13")
    )
  )
)

lazy val loggingSettings = Seq(
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.5.16",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
  ),
  // Drop and replace commons-logging with slf4j
  libraryDependencies += "org.slf4j" % "jcl-over-slf4j" % "2.0.16",
  excludeDependencies += ExclusionRule("commons-logging", "commons-logging")
)

lazy val scalacOptionsSettings = Def.settings(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-Wunused:all"
  ) ++ { if (isCI) Some("-Xfatal-warnings") else None }
)

lazy val scaladex = project
  .in(file("."))
  .aggregate(webclient, data, core.jvm, core.js, infra, server, template)
  .settings(Deployment(data, server))

lazy val template = project
  .in(file("modules/template"))
  .settings(
    scalacOptionsSettings,
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "com.github.nscala-time" %% "nscala-time" % V.nscalaTime,
      "com.typesafe" % "config" % "1.4.3",
      "org.apache.pekko" %% "pekko-http-core" % V.pekkoHttp,
      "org.scalatest" %% "scalatest" % V.scalatest % Test
    )
  )
  .dependsOn(core.jvm % "compile->compile;test->test")
  .enablePlugins(SbtTwirl)

lazy val infra = project
  .in(file("modules/infra"))
  .configs(IntegrationTest)
  .settings(
    scalacOptionsSettings,
    loggingSettings,
    libraryDependencies ++= Seq(
      "nl.gn0s1s" %% "elastic4s-client-esjava" % V.elastic4s,
      "org.flywaydb" % "flyway-core" % "8.5.13", // for database migration
      "org.apache.pekko" %% "pekko-stream" % V.pekko,
      "org.apache.pekko" %% "pekko-http" % V.pekkoHttp,
      "com.github.pjfanning" %% "pekko-http-circe" % "3.0.0",
      ("io.get-coursier" %% "coursier" % V.coursier).cross(CrossVersion.for3Use2_13),
      ("io.get-coursier" %% "coursier-sbt-maven-repository" % V.coursier).cross(CrossVersion.for3Use2_13),
      "com.github.blemale" %% "scaffeine" % "5.3.0",
      "org.tpolecat" %% "doobie-core" % V.doobie,
      "org.tpolecat" %% "doobie-h2" % V.doobie,
      "org.tpolecat" %% "doobie-postgres" % V.doobie,
      "org.tpolecat" %% "doobie-hikari" % V.doobie,
      ("org.tpolecat" %% "doobie-scalatest" % V.doobie % Test).cross(CrossVersion.for3Use2_13),
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-generic" % V.circe,
      "io.circe" %% "circe-parser" % V.circe,
      "org.scalatest" %% "scalatest" % V.scalatest % "test,it"
    ),
    Elasticsearch.settings(defaultPort = 9200),
    Postgres.settings(Compile, defaultPort = 5432, database = "scaladex"),
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
    Postgres.settings(Test, defaultPort = 5432, database = "scaladex-test"),
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
  .in(file("modules/webclient"))
  .settings(
    scalacOptionsSettings,
    scalacOptions -= "-Wunused", // don't report unused params
    scalacOptions += "-Wunused:imports",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.13.1",
      "org.endpoints4s" %%% "fetch-client" % "4.0.1"
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
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.2.0",
      "org.scalatest" %% "scalatest" % V.scalatest % "test,it",
      "org.apache.pekko" %% "pekko-testkit" % V.pekko % "test,it",
      "org.apache.pekko" %% "pekko-slf4j" % V.pekko,
      "org.apache.pekko" %% "pekko-serialization-jackson" % V.pekko,
      "org.apache.pekko" %% "pekko-actor-typed" % V.pekko,
      "org.apache.pekko" %% "pekko-stream-testkit" % V.pekko % Test,
      "org.apache.pekko" %% "pekko-http-testkit" % V.pekkoHttp % Test,
      "org.apache.pekko" %% "pekko-http-cors" % V.pekkoHttp,
      "com.softwaremill.pekko-http-session" %% "core" % "0.7.1",
      "org.apache.pekko" %% "pekko-http" % V.pekkoHttp,
      "org.endpoints4s" %% "pekko-http-server" % "2.0.1",
      "org.webjars" % "bootstrap-sass" % "3.4.1",
      "org.webjars" % "bootstrap-switch" % "3.3.4",
      "org.webjars" % "bootstrap-select" % "1.13.18",
      "org.webjars" % "chartjs" % "3.9.1",
      "org.webjars.npm" % "date-fns" % "4.1.0",
      "org.webjars.npm" % "chartjs-adapter-date-fns" % "3.0.0",
      "org.webjars" % "font-awesome" % "6.7.1",
      "org.webjars" % "jquery" % "3.7.1",
      "org.webjars.bower" % "select2" % "4.0.13",
      "org.webjars" % "swagger-ui" % "5.18.3"
    ),
    Compile / unmanagedResourceDirectories += (Assets / WebKeys.public).value,
    Compile / resourceGenerators += (Assets / WebKeys.assets).map(Seq(_)),
    fork := true,
    Compile / run / javaOptions ++= (infra / Compile / run / javaOptions).value,
    reStart / javaOptions ++= (infra / Compile / run / javaOptions).value,
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
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "fastparse" % "3.1.1",
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "org.endpoints4s" %%% "algebra" % "1.12.1",
      "org.scalatest" %%% "scalatest" % V.scalatest % Test,
      "org.jsoup" % "jsoup" % "1.18.3",
      "io.circe" %%% "circe-core" % V.circe,
      "io.circe" %%% "circe-generic" % V.circe,
      "io.circe" %%% "circe-parser" % V.circe
    )
  )

lazy val data = project
  .in(file("modules/data"))
  .settings(
    scalacOptionsSettings,
    loggingSettings,
    libraryDependencies ++= Seq(
      "com.github.nscala-time" %% "nscala-time" % V.nscalaTime,
      "org.apache.pekko" %% "pekko-stream" % V.pekko,
      "org.apache.pekko" %% "pekko-actor-typed" % V.pekko,
      "org.apache.pekko" %% "pekko-serialization-jackson" % V.pekko,
      "org.apache.pekko" %% "pekko-slf4j" % V.pekko,
      "org.apache.maven" % "maven-model-builder" % "4.0.0-rc-2",
      "org.jsoup" % "jsoup" % "1.18.3",
      "org.apache.ivy" % "ivy" % "2.5.3",
      "org.apache.pekko" %% "pekko-http" % V.pekkoHttp,
      "org.json4s" %% "json4s-native" % V.json4s,
      "org.scalatest" %% "scalatest" % V.scalatest % Test
    ),
    run / fork := true,
    Compile / run / javaOptions ++= (infra / Compile / run / javaOptions).value,
    Test / javaOptions ++= (infra / javaOptions).value
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(core.jvm % "compile->compile;test->test", infra)

lazy val V = new {
  val doobie = "0.13.4"
  val pekko = "1.1.3"
  val pekkoHttp = "1.1.0"
  val elastic4s = "8.16.0"
  val nscalaTime = "3.0.0"
  val scalatest = "3.2.19"
  val circe = "0.14.10"
  val json4s = "4.0.7"
  val coursier = "2.1.24"
}
