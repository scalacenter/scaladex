addSbtPlugin("io.spray"      % "sbt-revolver"  % "0.8.0")
addSbtPlugin("org.scala-js"  % "sbt-scalajs"   % "0.6.8")
addSbtPlugin("com.eed3si9n"  % "sbt-buildinfo" % "0.6.1")

libraryDependencies += {
  "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
}