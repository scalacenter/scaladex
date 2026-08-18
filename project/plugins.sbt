addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
addSbtPlugin("org.playframework.twirl" % "sbt-twirl" % "2.0.9")
addSbtPlugin("io.github.irundaia" % "sbt-sassify" % "1.5.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")
addSbtPlugin("io.gatling" % "gatling-sbt" % "4.19.1")

libraryDependencies ++= Seq(
  "org.testcontainers" % "postgresql" % "1.21.4",
  "org.testcontainers" % "elasticsearch" % "1.21.4",
  "org.tpolecat" %% "doobie-postgres" % "0.13.4"
)
