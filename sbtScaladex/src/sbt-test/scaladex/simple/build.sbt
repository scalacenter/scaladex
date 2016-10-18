import sbt._
import Keys._

// turn on testing in .travis.yml when this is resolve
lazy val testSetup = Seq(
  organization := "ch.epfl.scala.superlib4",
  version      := "1.1.5",
  scalaVersion := "2.11.8",

  scmInfo := Some(ScmInfo(url("https://github.com/scalacenter/scaladex"), "scm:git:git@github.com:scalacenter/scaladex.git")),
 
  scaladexBaseUri := uri("https://index.scala-lang.org"),
  scaladexTest := true,
  credentials in Scaladex += Credentials(Path.userHome / ".ivy2" / ".scaladex.credentials2")
  // or 
  // credentials in Scaladex := Credentials("Scaladex Realm", "localhost", "<github username>", "<github password>"),

  // TaskKey[Unit]("startServer") := {
  //   println("todo")
  // },
  // TaskKey[Unit]("stopServer") := {
  //   println("todo")
  // },
  // TaskKey[Unit]("checkNewRelease") := {
  //   println("todo")
  // }
)

lazy val simple = project.in(file("."))
  .settings(testSetup)
  .settings(
    publish in Scaladex := {}
  )
  .aggregate(m, n, o)
  .dependsOn(m, n, o)

lazy val m = (project in file("m"))
  .settings(testSetup)
  .enablePlugins(ScaladexPlugin)

lazy val n = (project in file("n"))
  .settings(testSetup)
  .enablePlugins(ScaladexPlugin)

lazy val o = (project in file("o"))
  .settings(testSetup)
  .enablePlugins(ScaladexPlugin)
