import Helper._

val commonSettings = Seq(
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq(
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
  scalacOptions in (Compile, console) -= "-Ywarn-unused-import",
  scalacOptions in (Test, console) -= "-Ywarn-unused-import",
  libraryDependencies += "com.lihaoyi" % "ammonite-repl" % "0.6.0" % "test" cross CrossVersion.full,
  initialCommands in (Test, console) := """ammonite.repl.Main().run()""",
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
      "com.lihaoyi" %%% "scalatags" % "0.5.2",
      "com.lihaoyi" %%% "upickle"   % Version.upickle,
      "com.lihaoyi" %%% "pprint"    % Version.upickle, // for prototyping
      "com.lihaoyi" %%% "autowire"  % "0.2.5"
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"                  %% "akka-http-experimental" % Version.akka,
      "com.softwaremill.akka-http-session" %% "core"                   % "0.2.6"
    )
  )
  
lazy val webappJS = webapp.js
  .dependsOn(model)
  .settings(
    libraryDependencies ++= {
      val scalajsReactVersion = "0.11.1"
      val scalaCssVersion = "0.4.1"
      Seq(
        "com.github.japgolly.scalacss"      %%% "core"      % scalaCssVersion,
        "com.github.japgolly.scalacss"      %%% "ext-react" % scalaCssVersion,
        "com.github.japgolly.scalajs-react" %%% "core"      % scalajsReactVersion,
        "com.github.japgolly.scalajs-react" %%% "extra"     % scalajsReactVersion
      )
    },
    jsDependencies ++= {
      val react = "org.webjars.bower" % "react" % "15.0.1"
      Seq(
        react / "react-with-addons.js" minified "react-with-addons.min.js" commonJSName "React",
        react / "react-dom.js"         minified "react-dom.min.js"         commonJSName "ReactDOM"       dependsOn "react-with-addons.js",
        react / "react-dom-server.js"  minified "react-dom-server.min.js"  commonJSName "ReactDOMServer" dependsOn "react-dom.js"
      )
    }
  )

lazy val webappJVM = webapp.jvm
  .settings(packageScalaJs(webappJS))
  .dependsOn(model, data)

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