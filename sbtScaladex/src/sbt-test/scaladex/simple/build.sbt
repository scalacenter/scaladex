import sbt._
import Keys._

// turn on testing in .travis.yml when this is resolve
lazy val testSetup = Seq(
  scaladexBaseUrl := "http://localhost:8080",
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

lazy val root = (project in file("."))
  .settings(testSetup)
  .enablePlugins(ScaladexPlugin)
