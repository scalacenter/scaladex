package ch.epfl.scala.index.model
package release

/**
  * the scala target eg. version / environment
  *
  * @param scalaVersion scala semantic version
  * @param scalaJsVersion the optional scalaJs semantic version
  */
case class ScalaTarget(
    scalaVersion: SemanticVersion,
    scalaJsVersion: Option[SemanticVersion] = None
) {

  /** simple modifier for display a nice name */
  def name =
    scalaJsVersion.map(v => s"Scala.js ${v.toString} ($scalaVersion)").getOrElse(s"Scala $scalaVersion")

  /** converting the scala version for support tags in elastic  - only major.minor to have small list */
  def supportName =
    scalaJsVersion
      .map(
          v => s"scala.js_${v.major}.${v.minor}"
      )
      .getOrElse(s"scala_${scalaVersion.major}.${scalaVersion.minor}")

  def sbt(artifact: String) = {
    artifact + scalaJsVersion.map("_sjs" + _).getOrElse("") + "_" + scalaVersion
  }

  def render = {
    scalaJsVersion match {
      case Some(v) =>
        s"scala-js ${v.toString()} (scala ${scalaVersion.toString()})"
      case None => s"scala ${scalaVersion.toString()}"
    }
  }

}
