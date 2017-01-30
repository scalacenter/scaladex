package ch.epfl.scala.index.model
package release

/*
SemanticVersion will only contain the information from the artifactId

if a library is not cross-published with full, then version.full == version.binary
in other words if we see cats_2.11 we will not infer the full scala version from
it's dependency on scala-library for example.

 */
case class ScalaTarget(
    scalaVersion: SemanticVersion,
    scalaJsVersion: Option[SemanticVersion],
    scalaNativeVersion: Option[SemanticVersion]
) {
  // def sbt(artifact: String) = {
  //   artifact + scalaJsVersion.map("_sjs" + _).getOrElse("") + "_" + scalaVersion
  // }
  def encode: String = ScalaTarget.encode(this)

  def render = {
    (scalaJsVersion, scalaNativeVersion) match {
      case (Some(jsVersion), _) =>
        s"scala-js ${jsVersion.toString} (scala ${scalaVersion.toString})"
      case (_, Some(nativeVersion)) =>
        s"scala-native ${nativeVersion.toString} (scala ${scalaVersion.toString})"
      case _ => s"scala ${scalaVersion.toString}"
    }
  }

  def targetType: String = {
    (scalaJsVersion, scalaNativeVersion) match {
      case (Some(_), _) => "JS"
      case (_, Some(_)) => "NATIVE"
      case _ => "JVM"
    }
  }
}

object ScalaTarget {

  def encode(target: ScalaTarget): String = {
    val scalaVersion = target.scalaVersion
    (target.scalaJsVersion, target.scalaNativeVersion) match {
      case (Some(scalaJsVersion), _) => s"_sjs${scalaJsVersion}_${scalaVersion}"
      case (_, Some(scalaNativeVersion)) => s"_native${scalaNativeVersion}_${scalaVersion}"
      case _ => s"_${scalaVersion}"
    }
  }

  def decode(code: String): Option[ScalaTarget] = {
    Artifact(code).map(_._2)
  }

  def scala(version: SemanticVersion) =
    ScalaTarget(scalaVersion = version, scalaJsVersion = None, scalaNativeVersion = None)

  def scalaJs(scalaVersion: SemanticVersion, scalaJsVersion: SemanticVersion) =
    ScalaTarget(scalaVersion = scalaVersion,
                scalaJsVersion = Some(scalaJsVersion),
                scalaNativeVersion = None)

  def scalaNative(version: SemanticVersion, scalaNativeVersion: SemanticVersion) =
    ScalaTarget(scalaVersion = version,
                scalaJsVersion = None,
                scalaNativeVersion = Some(scalaNativeVersion))

  def split(target: Option[ScalaTarget])
    : (Option[String], Option[String], Option[String], Option[String]) = {
    val targetType = target.map(_.targetType)
    val scalaVersion = target.map(_.scalaVersion.forceBinary.toString)
    val scalaJsVersion = target.flatMap(_.scalaJsVersion.map(_.forceBinary.toString))
    val scalaNativeVersion = target.flatMap(_.scalaNativeVersion.map(_.forceBinary.toString))

    (targetType, scalaVersion, scalaJsVersion, scalaNativeVersion)
  }
}
