package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.misc.{GeneralReference, ISO_8601_Date, MavenReference}
import ch.epfl.scala.index.model.release._

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

  /** split dependencies in 2 fields because elastic can't handle 2 different types
   * in one field. That is a simple workaround for that
   */
  scalaDependencies: Seq[ScalaDependency] = Seq(),
  javaDependencies: Seq[JavaDependency] = Seq(),
  reverseDependencies: Seq[ScalaDependency] = Seq()
) {
  def sbtInstall = {
    val scalaJs = reference.targets.scalaJsVersion.isDefined
    val crossFull = reference.targets.scalaVersion.patch.isDefined

    val (artifactOperator, crossSuffix) =
      if (scalaJs) ("%%%", "")
      else if (crossFull) ("%", " cross CrossVersion.full")
      else ("%%", "")

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
    if (mavenCentral) {
      import maven._
      // no frame
      // hosted on s3 at:
      // https://static.javadoc.io/$groupId/$artifactId/$version/index.html#package
      // HEAD to check 403 vs 200

      Some(s"https://www.javadoc.io/doc/$groupId/$artifactId/$version")
    } else None
  }

  lazy val orderedDependencies = scalaDependencies.sortBy(_.scope.contains(Scope.Test))
  lazy val orderedJavaDependencies = javaDependencies.sortBy(_.scope.contains(Scope.Test))
  lazy val orderedReverseDependencies = reverseDependencies.sortBy(_.scope.contains(Scope.Test))

  /** collect all unique organization/artifact dependency */
  lazy val uniqueOrderedReverseDependencies = {

    orderedReverseDependencies.foldLeft(Seq[ScalaDependency]()) { (current, next) =>

      if (current.exists(_.dependency.name == next.dependency.name)) current else current :+ next
    }
  }

  lazy val dependencyCount = scalaDependencies.size + javaDependencies.size

  /**
   * collect a list of version for a reverse dependency
   *
   * @param dep current looking dependency
   * @return
   */
  def versionsForReverseDependencies(dep: ScalaDependency): Seq[SemanticVersion] = {

    reverseDependencies.filter(d => d.dependency.name == dep.dependency.name).map(_.dependency.version)
  }
}


object Release {

  case class Reference(
    organization: String, // typelevel               | akka
    artifact: String, // cats-core               | akka-http-experimental
    version: SemanticVersion, // 0.6.0                   | 2.4.6
    targets: ScalaTargets // scalajs 0.6, scala 2.11 | scala 2.11
  ) extends GeneralReference {

    def name: String = s"$organization/$artifact"

    def httpUrl: String = s"/$organization/$artifact/$version"
  }

}