package ch.epfl.scala.index.model
package release

object ScalaTargetType {
  implicit val ordering: Ordering[ScalaTargetType] = {
    def toInt(v: ScalaTargetType): Int = {
      v match {
        case Jvm    => 5
        case Js     => 4
        case Native => 3
        case Sbt    => 2
        case Java   => 1
      }
    }

    Ordering.by(toInt)
  }
}

sealed trait ScalaTargetType
case object Jvm extends ScalaTargetType
case object Js extends ScalaTargetType
case object Native extends ScalaTargetType
case object Java extends ScalaTargetType
case object Sbt extends ScalaTargetType

/*
SemanticVersion will only contain the information from the artifactId

if a library is not cross-published with full, then version.full == version.binary
in other words if we see cats_2.11 we will not infer the full scala version from
it's dependency on scala-library for example.

 */
case class ScalaTarget(
    scalaVersion: SemanticVersion,
    scalaJsVersion: Option[SemanticVersion],
    scalaNativeVersion: Option[SemanticVersion],
    sbtVersion: Option[SemanticVersion]
) extends Ordered[ScalaTarget] {

  def render = {
    (scalaJsVersion, scalaNativeVersion, sbtVersion) match {
      case (Some(jsVersion), _, _) =>
        s"scala-js ${jsVersion.toString} (scala ${scalaVersion.toString})"

      case (_, Some(nativeVersion), _) =>
        s"scala-native ${nativeVersion.toString} (scala ${scalaVersion.toString})"

      case (_, _, Some(sbtVersion)) =>
        s"sbt ${sbtVersion.toString} (scala ${scalaVersion.toString})"

      case _ =>
        s"scala ${scalaVersion.toString}"
    }
  }

  def targetType: ScalaTargetType = {
    (scalaJsVersion, scalaNativeVersion, sbtVersion) match {
      case (Some(_), _, _) => Js
      case (_, Some(_), _) => Native
      case (_, _, Some(_)) => Sbt
      case _               => Jvm
    }
  }

  def encode: String = {
    (scalaJsVersion, scalaNativeVersion, sbtVersion) match {
      case (Some(scalaJsVersion), _, _) =>
        s"_sjs${scalaJsVersion}_${scalaVersion}"

      case (_, Some(scalaNativeVersion), _) =>
        s"_native${scalaNativeVersion}_${scalaVersion}"

      case (_, _, Some(sbtVersion)) =>
        s"_${scalaVersion}_${sbtVersion}"

      case _ =>
        s"_${scalaVersion}"
    }
  }

  private final val LT = -1
  private final val GT = 1
  private final val EQ = 0

  // Scala > Js > Native

  override def compare(that: ScalaTarget): Int =
    ScalaTarget.ordering.compare(this, that)
}

object ScalaTarget {

  implicit val ordering: Ordering[ScalaTarget] = Ordering.by { target =>
    (
      target.targetType,
      target.scalaVersion,
      target.scalaJsVersion,
      target.scalaNativeVersion,
      target.sbtVersion
    )
  }

  def decode(code: String): Option[ScalaTarget] = {
    Artifact(code).map(_._2)
  }

  def scala(scalaVersion: SemanticVersion) =
    ScalaTarget(scalaVersion = scalaVersion,
                scalaJsVersion = None,
                scalaNativeVersion = None,
                sbtVersion = None)

  def scalaJs(scalaVersion: SemanticVersion, scalaJsVersion: SemanticVersion) =
    ScalaTarget(scalaVersion = scalaVersion,
                scalaJsVersion = Some(scalaJsVersion),
                scalaNativeVersion = None,
                sbtVersion = None)

  def scalaNative(scalaVersion: SemanticVersion,
                  scalaNativeVersion: SemanticVersion) =
    ScalaTarget(scalaVersion = scalaVersion,
                scalaJsVersion = None,
                scalaNativeVersion = Some(scalaNativeVersion),
                sbtVersion = None)

  def sbt(scalaVersion: SemanticVersion, sbtVersion: SemanticVersion) =
    ScalaTarget(scalaVersion = scalaVersion,
                scalaJsVersion = None,
                scalaNativeVersion = None,
                sbtVersion = Some(sbtVersion))

  def split(target: Option[ScalaTarget]): (Option[ScalaTargetType],
                                           Option[String],
                                           Option[String],
                                           Option[String],
                                           Option[String],
                                           Option[String]) = {
    val targetType =
      target.map(_.targetType)

    val fullScalaVersion =
      target.map(_.scalaVersion.toString)

    val scalaVersion =
      target.map(_.scalaVersion.forceBinary.toString)

    val scalaJsVersion = for {
      target <- target
      scalaJsVersion <- target.scalaJsVersion
    } yield scalaJsVersion match {
      case SemanticVersion(0, minor, _, _, _, _) => SemanticVersion(0, minor).toString
      case _ => SemanticVersion(scalaJsVersion.major).toString
    }

    val scalaNativeVersion =
      target.flatMap(_.scalaNativeVersion.map(_.forceBinary.toString))

    val sbtVersion =
      target.flatMap(_.sbtVersion.map(_.forceBinary.toString))

    (targetType,
     fullScalaVersion,
     scalaVersion,
     scalaJsVersion,
     scalaNativeVersion,
     sbtVersion)
  }
}
