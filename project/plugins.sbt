addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")
addSbtPlugin("com.typesafe.play" % "sbt-twirl" % "1.6.0-RC3")
addSbtPlugin("io.github.irundaia" % "sbt-sassify" % "1.5.2")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.16")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.1")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.4")

libraryDependencies ++= Seq(
  "org.testcontainers" % "postgresql" % "1.18.3",
  "org.testcontainers" % "elasticsearch" % "1.18.3",
  "org.tpolecat" %% "doobie-postgres" % "0.13.4"
)
