addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.6.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.3.2")
addSbtPlugin("org.madoushi.sbt" % "sbt-sass" % "0.9.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.1.1")
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.16")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
