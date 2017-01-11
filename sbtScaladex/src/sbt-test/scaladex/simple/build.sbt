import sbt._
import Keys._

// turn on testing in .travis.yml when this is resolve
lazy val testSetup = Seq(
  organization := "ch.epfl.scala.superlib",
  version := "1.1.8",
  scalaVersion := "2.11.8",
  scmInfo := Some(
    ScmInfo(url("https://github.com/scalacenter/scaladex"),
            "scm:git:git@github.com:scalacenter/scaladex.git")),
  // scaladexBaseUri := uri("https://scaladex.scala-lang.org"),
  scaladexBaseUri := uri("http://localhost:8080"),
  scaladexTest := true,
  // credentials in Scaladex += Credentials(Path.userHome / ".ivy2" / ".scaladex.credentials2")
  credentials in Scaladex += Credentials(Path.userHome / ".ivy2" / ".scaladex.credentials")
  // or
  // credentials in Scaladex := Credentials("Scaladex Realm", "localhost", "token", "<github personnal token>"),
)

lazy val simple = project
  .in(file("."))
  .settings(testSetup)
  .settings(publish in Scaladex := {})
  .aggregate(m, n, o)
  .dependsOn(m, n, o)

lazy val m = (project in file("m")).settings(testSetup).enablePlugins(ScaladexPlugin)

lazy val n = (project in file("n")).settings(testSetup).enablePlugins(ScaladexPlugin)

lazy val o = (project in file("o")).settings(testSetup).enablePlugins(ScaladexPlugin)
