addSbtPlugin("com.github.tototoshi" % "sbt-slick-codegen"   % "1.2.0")
addSbtPlugin("org.flywaydb"         % "flyway-sbt"          % "4.0.3")
addSbtPlugin("io.spray"             % "sbt-revolver"        % "0.8.0")
addSbtPlugin("com.eed3si9n"         % "sbt-buildinfo"       % "0.6.1")
addSbtPlugin("com.typesafe.sbt"     % "sbt-twirl"           % "1.1.1")
addSbtPlugin("org.madoushi.sbt"     % "sbt-sass"            % "0.9.3")
addSbtPlugin("com.typesafe.sbt"     % "sbt-native-packager" % "1.1.1")
addSbtPlugin("me.lessis"            % "bintray-sbt"         % "0.3.0")
addSbtPlugin("org.scala-js"         % "sbt-scalajs"         % "0.6.10")
addSbtPlugin("com.geirsson"         % "sbt-scalafmt"        % "0.2.11")

libraryDependencies ++= Seq(
  "org.scala-sbt"  % "scripted-plugin" % sbtVersion.value,
  "org.postgresql" % "postgresql"      % "9.4-1201-jdbc41"
)

resolvers += "Flyway" at "https://flywaydb.org/repo"