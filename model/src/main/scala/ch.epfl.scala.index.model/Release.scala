package ch.epfl.scala.index.model

// typelevel/cats-core (scalajs 0.6, scala 2.11) 0.6.0
case class Release(
  // famous maven triple: org.typelevel - cats-core_sjs0.6_2.11 - 0.6.0
  maven: MavenReference,
  // similar to maven but with a clean artifact name
  reference: Release.Reference,
  // human readable name (ex: Apache Spark)
  name: Option[String] = None,
  description: Option[String] = None,
  // potentially various dates because bintray allows republishing
  releaseDates: List[ISO_8601_Date] = Nil,
  // availability on the central repository
  mavenCentral: Boolean = false,
  licenses: Set[License] = Set(),
  dependencies: Set[Release.Reference] = Set(),
  reverseDependencies: Set[Release.Reference] = Set()
) {
  def sbtInstall = {
    val scalaJs = reference.targets.scalaJsVersion.isDefined
    val crossFull = reference.targets.scalaVersion.patch.isDefined
  
    val (artifactOperator, crossSuffix) =
      if (scalaJs)       ("%%%",                         "")
      else if(crossFull) (  "%", " cross CrossVersion.full")
      else               ( "%%",                         "")

    s""""${maven.groupId}" $artifactOperator "${reference.artifact}" % "${reference.version}$crossSuffix""""
  }
  def mavenInstall = {
    import maven._
    s"""|<dependency>
        |  <groupId>$groupId</groupId>
        |  <artifactId>$artifactId</artifactId>
        |  <version>$version</version>
        |</dependency>""".stripMargin
  }
  def gradleInstall = {
    import maven._
    s"compile group: '$groupId', name: '$artifactId', version: '$version'"
  }
  def scalaDocURI: Option[String] = {
    if(mavenCentral) {
      import maven._
      // no frame
      // hosted on s3 at:
      // https://static.javadoc.io/$groupId/$artifactId/$version/index.html#package
      // HEAD to check 403 vs 200

      Some(s"https://www.javadoc.io/doc/$groupId/$artifactId/$version")
    } else None
  }
}
object Release{
  case class Reference(
    organization: String,     // typelevel               | akka
    artifact: String,         // cats-core               | akka-http-experimental
    version: SemanticVersion, // 0.6.0                   | 2.4.6
    targets: ScalaTargets     // scalajs 0.6, scala 2.11 | scala 2.11
  )
}

// com.typesafe.akka - akka-http-experimental_2.11 - 2.4.6 | org.typelevel - cats-core_sjs0.6_2.11 - 0.6.0
case class MavenReference(
  groupId: String,      // org.typelevel         | com.typesafe.akka
  artifactId: String,   // cats-core_sjs0.6_2.11 | akka-http-experimental_2.11
  version: String       // 0.6.0                 | 2.4.6
)

case class ScalaTargets(scalaVersion: SemanticVersion, scalaJsVersion: Option[SemanticVersion] = None) {

  /** simple modifier for display a nice name */
  lazy val name = scalaJsVersion.map(v => s"Scala.js ${v.toString} ($scalaVersion)").getOrElse(s"Scala $scalaVersion")

  /** simple modifier for ordering */
  lazy val orderName: String = scalaJsVersion.map(v => s"${scalaVersion.toString.replace(".", "")}_${v.toString.replace(".", "")}").getOrElse(scalaVersion.toString.replace(".", ""))
}

case class ISO_8601_Date(value: String) // 2016-05-20T12:48:52.533-04:00