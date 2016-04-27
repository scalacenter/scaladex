import Helper._

val commonSettings = Seq(
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:experimental.macros",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Xexperimental",
    "-Xfatal-warnings",
    "-Xfuture",
    "-Xlint",
    "-Ybackend:GenBCode",
    "-Ydelambdafy:method",
    "-Yinline-warnings",
    "-Yno-adapted-args",
    "-Yrangepos",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  ),
  scalacOptions in (Compile, console) --= Seq(
    "-Yno-imports",
    "-Ywarn-unused-import"
  ),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "utest" % "0.4.3" % "test"
  ),
  testFrameworks += new TestFramework("utest.runner.Framework"),
  organization := "ch.epfl.scala.index",
  version      := "0.1.2"
)

lazy val webapp = crossProject
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalacss" %%% "core"      % "0.4.1",
      "com.lihaoyi"                  %%% "scalatags" % "0.5.2",
      "com.lihaoyi"                  %%% "upickle"   % "0.3.8",
      "com.lihaoyi"                  %%% "autowire"  % "0.2.5"
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-experimental" % "2.4.4"
    ) 
  )
  
lazy val webappJS = webapp.js
  .dependsOn(model)

lazy val webappJVM = webapp.jvm
  .settings(packageScalaJs(webappJS))
  .dependsOn(model, data)

lazy val model = project
  .settings(commonSettings: _*)
  .enablePlugins(ScalaJSPlugin)

lazy val data = project
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"      %% "akka-http-experimental"            % "2.4.4",
      "com.typesafe.akka"      %% "akka-http-spray-json-experimental" % "2.4.4",
      "org.scala-lang.modules" %% "scala-xml"                         % "1.0.5",
      "com.github.nscala-time" %% "nscala-time"                       % "2.10.0",
      "me.tongfei"              % "progressbar"                       % "0.4.0",
      "org.apache.maven"        % "maven-model-builder"               % "3.3.9",
      "com.lihaoyi"            %% "fastparse"                         % "0.3.7"
    ),
    securityManager in Backend := false,
    timeout in Backend := {
      import scala.concurrent.duration._
      1.minute
    }
  )
  .enablePlugins(ScalaKataPlugin)
  .dependsOn(model)

