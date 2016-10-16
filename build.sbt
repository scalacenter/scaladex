import ScalaJSHelper._
import org.scalajs.sbtplugin.cross.CrossProject

scalafmtConfig in ThisBuild := Some(file(".scalafmt"))

lazy val akkaVersion = "2.4.7"
lazy val upickleVersion = "0.4.1"
lazy val scalatagsVersion = "0.6.0"
lazy val autowireVersion = "0.2.5"

lazy val baseSettings = Seq(
  organization := "ch.epfl.scala.index",
  version      := "1.0.0-SNAPSHOT"
)

lazy val commonSettings = Seq(
  resolvers += Resolver.typesafeIvyRepo("releases"),
  scalaVersion := "2.11.8",
  scalacOptions := Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
    "-Ybackend:GenBCode",
    "-Ydelambdafy:method",
    "-Yinline-warnings",
    "-Yno-adapted-args",
    "-Yrangepos",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused-import",
    "-Ywarn-value-discard",
    "-Xmax-classfile-name", "78"
  ),
  console <<= console in Test,
  scalacOptions in (Test, console) -= "-Ywarn-unused-import",
  scalacOptions in (Compile, consoleQuick) -= "-Ywarn-unused-import",
  libraryDependencies += "com.lihaoyi" % "ammonite-repl" % "0.6.0" % "test" cross CrossVersion.full,
  initialCommands in (Test, console) := """ammonite.repl.Main().run()""",
  libraryDependencies += "org.specs2" %% "specs2-core" % "3.8.4" % "test",
  scalacOptions in Test ++= Seq("-Yrangepos")
) ++ baseSettings ++
  addCommandAlias("start", "reStart")

lazy val template = project
  .settings(commonSettings)
  .settings(
    scalacOptions -= "-Ywarn-unused-import",
    libraryDependencies ++= Seq(
      "com.typesafe"            % "config"         % "1.3.0",
      "com.github.nscala-time" %% "nscala-time"    % "2.12.0",
      "com.typesafe.akka"      %% "akka-http-core" % akkaVersion
    )
  )
  .dependsOn(model)
  .enablePlugins(SbtTwirl)

lazy val shared = crossProject
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % scalatagsVersion,
      "com.lihaoyi" %%% "upickle"   % upickleVersion,
      "com.lihaoyi" %%% "autowire"  % autowireVersion
    )
  )
lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

lazy val client = project
  .settings(commonSettings)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJs)

lazy val server = project
  .settings(commonSettings)
  .settings(packageScalaJS(client))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"                  %% "akka-http-experimental" % akkaVersion,
      "com.github.swagger-akka-http"       %% "swagger-akka-http"      % "0.7.2",
      "ch.megard"                          %% "akka-http-cors"         % "0.1.7",
      "com.softwaremill.akka-http-session" %% "core"                   % "0.2.7",
      "com.typesafe.scala-logging"         %% "scala-logging"          % "3.4.0",
      "ch.qos.logback"                      % "logback-classic"        % "1.1.7",
      "org.webjars.bower"                   % "bootstrap-sass"         % "3.3.6",
      "org.webjars.bower"                   % "bootstrap-switch"       % "3.3.2",
      "org.webjars.bower"                   % "bootstrap-select"       % "1.10.0",
      "org.webjars.bower"                   % "font-awesome"           % "4.6.3",
      "org.webjars.bower"                   % "jQuery"                 % "2.2.4",
      "org.webjars.bower"                   % "select2"                % "4.0.3",
      "com.lihaoyi"                       %%% "scalatags"              % scalatagsVersion
    ),
    packageBin in Universal <<= (packageBin in Universal).dependsOn(WebKeys.assets in Assets),
    reStart <<= reStart.dependsOn(WebKeys.assets in Assets),
    unmanagedResourceDirectories in Compile += (WebKeys.public in Assets).value,
    // unmanagedResourceDirectories in (Compile, packageBin) <<= unmanagedResourceDirectories in Compile,
    javaOptions in reStart += "-Xmx3g",
    packageName in Universal := "scaladex"
  )
  .dependsOn(template, data, sharedJvm)
  .enablePlugins(SbtSass, JavaServerAppPackaging)

lazy val model = project
  .settings(commonSettings)
  .settings(
    libraryDependencies += "com.lihaoyi" %% "fastparse" % "0.3.7"
  )

lazy val data = project
  .settings(commonSettings)
  .settings(
    resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
    libraryDependencies ++= Seq(
      "com.sksamuel.elastic4s" %% "elastic4s-core"                    % "2.3.0",
      "com.typesafe.akka"      %% "akka-stream"                       % akkaVersion,
      "org.json4s"             %% "json4s-native"                     % "3.4.0",
      "com.github.nscala-time" %% "nscala-time"                       % "2.12.0",
      "me.tongfei"              % "progressbar"                       % "0.4.0",
      "org.apache.maven"        % "maven-model-builder"               % "3.3.9",
      "ch.qos.logback"          % "logback-classic"                   % "1.1.7",
      "org.jsoup"               % "jsoup"                             % "1.9.2",
      "com.typesafe.play"      %% "play-ws"                           % "2.5.4"
    ),
    buildInfoPackage := "build.info",
    buildInfoKeys := Seq[BuildInfoKey](baseDirectory in ThisBuild),
    javaOptions in reStart += "-Xmx3g"
  )
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(model)

// to publish plugin
// follow: http://www.scala-sbt.org/0.13/docs/Bintray-For-Plugins.html
// then add a new package ()
// name: sbt-scaladex, license: MIT, version control: git@github.com:scalacenter/scaladex.git
// to be available without a resolver
// follow: http://www.scala-sbt.org/0.13/docs/Bintray-For-Plugins.html#Linking+your+package+to+the+sbt+organization
lazy val sbtScaladex = project
  .settings(baseSettings)
  .settings(ScriptedPlugin.scriptedSettings)
  .settings(
    name := "sbt-scaladex",
    sbtPlugin := true,
    scalaVersion := "2.10.6",
    scriptedLaunchOpts := scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog := false,

    bintrayRepository := "sbt-plugins",
    bintrayOrganization := None,
    scalafmt := {}

  ).enablePlugins(BintrayPlugin)
