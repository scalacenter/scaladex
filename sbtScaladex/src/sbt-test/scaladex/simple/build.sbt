import sbt._
import Keys._

lazy val testSetup = Seq(
  scaladexBaseUrl := "http://localhost:8080",
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

lazy val root = (project in file("."))
  .settings(testSetup)
  .enablePlugins(ScaladexPlugin)
