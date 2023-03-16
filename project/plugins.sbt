addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.typesafe.play" % "sbt-twirl" % "1.6.0-RC2")
addSbtPlugin("io.github.irundaia" % "sbt-sassify" % "1.5.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.4")

libraryDependencies ++= Seq(
  "org.testcontainers" % "postgresql" % "1.17.6",
  "org.testcontainers" % "elasticsearch" % "1.17.6",
  "org.tpolecat" %% "doobie-postgres" % "0.13.4"
)
