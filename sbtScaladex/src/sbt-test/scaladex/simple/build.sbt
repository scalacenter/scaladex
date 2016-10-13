import sbt._
import Keys._

// turn on testing in .travis.yml when this is resolve
lazy val testSetup = Seq(
  organization := "ch.epfl.scala.superlib4",
  version      := "1.0.0",
  scalaVersion := "2.11.8",
  scmInfo := Some(ScmInfo(url("https://github.com/scalacenter/scaladex"), "scm:git:git@github.com:scalacenter/scaladex.git")),
 
  scaladexBaseUri := uri("http://localhost:8080"),
  credentials in Scaladex += Credentials(Path.userHome / ".ivy2" / ".scaladex.credentials"),
  // or 
  // credentials in Scaladex := Credentials("Scaladex Realm", "localhost", "<github username>", "<github password>"),

  TaskKey[Unit]("startServer") := {
    println("todo")
  },
  TaskKey[Unit]("stopServer") := {
    println("todo")
  },
  TaskKey[Unit]("checkNewRelease") := {
    println("todo")
  }
)

lazy val superlib5 = (project in file("."))
  .settings(testSetup)
  .enablePlugins(ScaladexPlugin)


lazy val supersuperlib5 = (project in file("super"))
  .settings(testSetup)
  .enablePlugins(ScaladexPlugin)
