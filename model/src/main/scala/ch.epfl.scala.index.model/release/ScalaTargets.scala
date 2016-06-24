package ch.epfl.scala.index.model.release

import ch.epfl.scala.index.model.SemanticVersion

/**
 * the scala target eg. version / environment
 * @param scalaVersion scala semantic version
 * @param scalaJsVersion the optional scalaJs semantic version
 */
case class ScalaTargets(
  scalaVersion: SemanticVersion,
  scalaJsVersion: Option[SemanticVersion] = None
) {

  /** simple modifier for display a nice name */
  lazy val name: String = scalaJsVersion.map(v =>
    s"Scala.js ${v.toString} ($scalaVersion)"
  ).getOrElse(s"Scala $scalaVersion")

  /** converting the scala version for support tags in elastic  - only major.minor to have small list */
  lazy val supportName: String = scalaJsVersion.map(
    v => s"scala.js_${v.major}.${v.minor}"
  ).getOrElse(s"scala_${scalaVersion.major}.${scalaVersion.minor}")

  /** simple modifier for ordering */
  lazy val orderName: String = scalaJsVersion.map(v =>
    s"${scalaVersion.toString.replace(".", "")}_${v.toString.replace(".", "")}"
  ).getOrElse(scalaVersion.toString.replace(".", ""))
}
