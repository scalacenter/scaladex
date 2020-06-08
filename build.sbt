import ScalaJSHelper._
import sbtcrossproject.CrossPlugin.autoImport.crossProject

import Deployment.githash

val playJsonVersion = "2.9.0"
val akkaVersion = "2.5.3"
val akkaHttpVersion = "10.0.11"
val elastic4sVersion = "5.4.5"
lazy val scalaTestVersion = "3.0.1"

def akka(module: String) =
  "com.typesafe.akka" %% ("akka-" + module) % akkaVersion

def akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion

val playJson =
  libraryDependencies += "com.typesafe.play" %%% "play-json" % playJsonVersion

val nscalaTime = "com.github.nscala-time" %% "nscala-time" % "2.14.0"

lazy val logging =
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "com.getsentry.raven" % "raven-logback" % "8.0.3"
  )

lazy val baseSettings = Seq(
  organization := "ch.epfl.scala.index",
  version := s"0.2.0+${githash()}"
)

lazy val ammoniteSettings = Seq(
  libraryDependencies += "com.lihaoyi" % "ammonite" % "2.0.4" % Test cross CrossVersion.full,
  sourceGenerators in Test += Def.task {
    val file = (sourceManaged in Test).value / "amm.scala"
    IO.write(file, """object amm extends App { ammonite.Main.main(args) }""")
    Seq(file)
  }.taskValue,
  (fullClasspath in Test) ++= {
    (updateClassifiers in Test).value.configurations
      .find(_.configuration.name == Test.name)
      .get
      .modules
      .flatMap(_.artifacts)
      .collect { case (a, f) if a.classifier.contains("sources") => f }
  }
)

lazy val commonSettings = Seq(
  resolvers += Resolver.typesafeIvyRepo("releases"),
  scalaVersion := "2.12.10",
  scalacOptions := Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  ),
  libraryDependencies += "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  javaOptions in reStart ++= {
    val base = (baseDirectory in ThisBuild).value

    val devCredentials = base / "../scaladex-dev-credentials/application.conf"

    val addDevCredentials =
      if (devCredentials.exists) Seq(s"-Dconfig.file=$devCredentials")
      else Seq()

    addDevCredentials
  }
) ++ baseSettings ++
  addCommandAlias("start", "reStart") ++ logging ++ ammoniteSettings

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
      nscalaTime,
      "com.typesafe" % "config" % "1.3.1",
      akkaHttpCore
    )
  )
  .dependsOn(model)
  .enablePlugins(SbtTwirl)

lazy val api = crossProject(JSPlatform, JVMPlatform)
  .settings(commonSettings)
  .settings(playJson)

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
  .settings(playJson)
  .settings(
    libraryDependencies ++= Seq(
      akka("testkit") % Test,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "ch.megard" %% "akka-http-cors" % "0.2.1",
      "com.softwaremill.akka-http-session" %% "core" % "0.5.3",
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "org.webjars.bower" % "bootstrap-sass" % "3.3.6",
      "org.webjars.bower" % "bootstrap-switch" % "3.3.2",
      "org.webjars.bower" % "bootstrap-select" % "1.10.0",
      "org.webjars.bower" % "font-awesome" % "4.6.3",
      "org.webjars.bower" % "jQuery" % "2.2.4",
      "org.webjars.bower" % "raven-js" % "3.11.0",
      "org.webjars.bower" % "select2" % "4.0.3"
    ),
    packageBin in Universal := (packageBin in Universal)
      .dependsOn(WebKeys.assets in Assets)
      .value,
    reStart := reStart.dependsOn(WebKeys.assets in Assets).evaluated,
    unmanagedResourceDirectories in Compile += (WebKeys.public in Assets).value,
    javaOptions in reStart ++= Seq("-Xmx4g")
  )
  .dependsOn(template, data, api.jvm)
  .enablePlugins(SbtSassify, JavaServerAppPackaging)

lazy val model = project
  .settings(commonSettings)
  .settings(
    libraryDependencies += "com.lihaoyi" %% "fastparse" % "2.2.3"
  )

lazy val data = project
  .settings(commonSettings)
  .settings(
    resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
    libraryDependencies ++= Seq(
      nscalaTime,
      "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion,
      "com.sksamuel.elastic4s" %% "elastic4s-embedded" % elastic4sVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "org.json4s" %% "json4s-native" % "3.5.3",
      "org.spire-math" %% "jawn-json4s" % "0.11.0",
      "me.tongfei" % "progressbar" % "0.5.5",
      "org.apache.maven" % "maven-model-builder" % "3.3.9",
      "org.jsoup" % "jsoup" % "1.10.1",
      "com.typesafe.play" %% "play-ahc-ws" % "2.6.12",
      "org.apache.ivy" % "ivy" % "2.4.0",
      "com.typesafe.akka" %% "akka-http" % "10.0.10",
      "de.heikoseeberger" %% "akka-http-json4s" % "1.19.0",
      "org.json4s" %% "json4s-native" % "3.5.3",
      "com.jsuereth" %% "scala-arm" % "2.0"
    ),
    buildInfoPackage := "build.info",
    buildInfoKeys := Seq[BuildInfoKey](baseDirectory in ThisBuild),
    javaOptions in reStart ++= Seq("-Xmx4g")
  )
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(model)
