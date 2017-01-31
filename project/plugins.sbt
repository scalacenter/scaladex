addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.1.1")
addSbtPlugin("org.madoushi.sbt" % "sbt-sass" % "0.9.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.10")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.5.4")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M15-1")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
