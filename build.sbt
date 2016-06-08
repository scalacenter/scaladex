import Helper._

lazy val baseSettings = Seq(
  organization := "ch.epfl.scala.index",
  version      := "0.1.2"
)

val commonSettings = Seq(
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
    "-Ywarn-value-discard"
  ),
  scalacOptions in (Test, console) -= "-Ywarn-unused-import",
  libraryDependencies += "com.lihaoyi" % "ammonite-repl" % "0.6.0" % "test" cross CrossVersion.full,
  initialCommands in (Test, console) := """ammonite.repl.Main().run()""",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "utest" % "0.4.3" % "test"
  ),
  testFrameworks += new TestFramework("utest.runner.Framework")
) ++ baseSettings

lazy val template = project
  .settings(commonSettings: _*)
  .settings(scalacOptions -= "-Ywarn-unused-import")
  .dependsOn(model)
  .enablePlugins(SbtTwirl)

lazy val webapp = crossProject
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "scalatags" % "0.5.2",
      "com.lihaoyi" %%% "upickle"  % Version.upickle,
      "com.lihaoyi" %%% "autowire" % "0.2.5"
    )
  )

lazy val webappJS = webapp.js
  .dependsOn(model)

lazy val webappJVM = webapp.jvm
  .settings(packageScalaJs(webappJS))
  .settings(
    resolvers ++= Seq(
      Resolver.bintrayRepo("btomala", "maven"),
      Resolver.bintrayRepo("hseeberger", "maven")
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka"                  %% "akka-http-experimental" % Version.akka,
      "com.softwaremill.akka-http-session" %% "core"                   % "0.2.6",
      "com.typesafe.scala-logging"         %% "scala-logging"          % "3.4.0",
      "ch.qos.logback"                      % "logback-classic"        % "1.1.7",
      "org.webjars.bower"                   % "bootstrap-sass"         % "3.3.6",
      "org.webjars.bower"                   % "bootstrap-select"       % "1.10.0"
    ),
    reStart <<= reStart.dependsOn(WebKeys.assets in Assets),
    unmanagedResourceDirectories in Compile += (WebKeys.public in Assets).value
  )
  .dependsOn(model, data, template)
  .enablePlugins(SbtSass)

lazy val model = project
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies += "com.lihaoyi" %% "upickle" % Version.upickle
  )
  .enablePlugins(ScalaJSPlugin)

lazy val data = project
  .settings(commonSettings: _*)
  .settings(
    resolvers += Resolver.bintrayRepo("hseeberger", "maven"),
    libraryDependencies ++= Seq(
      "com.sksamuel.elastic4s" %% "elastic4s-core"                    % "2.3.0",
      "com.typesafe.akka"      %% "akka-http-experimental"            % Version.akka,
      "com.typesafe.akka"      %% "akka-http-spray-json-experimental" % Version.akka,
      "de.heikoseeberger"      %% "akka-http-json4s"                  % "1.7.0",
      "org.json4s"             %% "json4s-native"                     % "3.3.0",
      "de.heikoseeberger"      %% "akka-http-circe"                   % "1.7.0",
      "org.scala-lang.modules" %% "scala-xml"                         % "1.0.5",
      "com.github.nscala-time" %% "nscala-time"                       % "2.10.0",
      "com.lihaoyi"            %% "fastparse"                         % "0.3.7",
      "me.tongfei"              % "progressbar"                       % "0.4.0",
      "org.apache.maven"        % "maven-model-builder"               % "3.3.9",
      "ch.qos.logback"          % "logback-classic"                   % "1.1.7",
      "org.jsoup"               % "jsoup"                             % "1.9.2"
    ),
    buildInfoPackage := "build.info",
    buildInfoKeys := Seq[BuildInfoKey](baseDirectory in ThisBuild),
    javaOptions in reStart += "-Xmx2g"
  )
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(model)

lazy val sbtScaladex = project
  .settings(baseSettings: _*)
  .settings(ScriptedPlugin.scriptedSettings: _*)
  .settings(
    name := "sbt-scaladex",
    sbtPlugin := true,
    scalaVersion := "2.10.6",
    scriptedLaunchOpts := scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog := false,

    /* Dont publish */
    publishArtifact in packageDoc := false,
    sources in(Compile, doc) := Seq.empty,
    publishArtifact in(Compile, packageDoc) := false
  )