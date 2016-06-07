import sbt._
import Keys._

lazy val root = (project in file("."))
  .enablePlugins(HelloPlugin)