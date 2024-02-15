addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
addSbtPlugin("org.playframework.twirl" % "sbt-twirl" % "2.0.3")
addSbtPlugin("io.github.irundaia" % "sbt-sassify" % "1.5.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.15.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.1")

libraryDependencies ++= Seq(
  "org.testcontainers" % "postgresql" % "1.19.5",
  "org.testcontainers" % "elasticsearch" % "1.19.5",
  "org.tpolecat" %% "doobie-postgres" % "0.13.4"
)
