addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.5.1")
addSbtPlugin("io.github.irundaia" % "sbt-sassify" % "1.5.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.9")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.10.1")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.1")

libraryDependencies ++= Seq(
  "org.testcontainers" % "postgresql" % "1.17.5",
  "org.testcontainers" % "elasticsearch" % "1.17.5",
  "org.tpolecat" %% "doobie-postgres" % "0.13.4"
)
