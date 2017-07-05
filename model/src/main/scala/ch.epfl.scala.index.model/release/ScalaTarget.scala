package ch.epfl.scala.index.model
package release

object ScalaTargetType {
  implicit val ordering: Ordering[ScalaTargetType] = {
    def toInt(v: ScalaTargetType): Int = {
      v match {
        case Jvm    => 4
        case Js     => 3
        case Native => 2
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
) extends Ordered[ScalaTarget] {
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

  def targetType: ScalaTargetType = {
    (scalaJsVersion, scalaNativeVersion) match {
      case (Some(_), _) => Js
      case (_, Some(_)) => Native
      case _            => Jvm
    }
  }

  private final val LT = -1
  private final val GT = 1
  private final val EQ = 0

  private def isScalaJs = scalaJsVersion.isDefined
  private def isScalaNative = scalaNativeVersion.isDefined
  private def isScala = !isScalaJs && !isScalaNative

  // Scala > Js > Native

  private val ordering: Ordering[ScalaTarget] = Ordering.by { target =>
    (
      target.targetType,
      target.scalaVersion,
      target.scalaJsVersion,
      target.scalaNativeVersion
    )
  }
  override def compare(that: ScalaTarget): Int =
    ordering.compare(this, that)
}

object ScalaTarget {
  implicit def ordering = new Ordering[ScalaTarget] {
    def compare(t1: ScalaTarget, t2: ScalaTarget): Int =
      t1.compare(t2)
  }

  def encode(target: ScalaTarget): String = {
    val scalaVersion = target.scalaVersion
    (target.scalaJsVersion, target.scalaNativeVersion) match {
      case (Some(scalaJsVersion), _) =>
        s"_sjs${scalaJsVersion}_${scalaVersion}"
      case (_, Some(scalaNativeVersion)) =>
        s"_native${scalaNativeVersion}_${scalaVersion}"
      case _ => s"_${scalaVersion}"
    }
  }

  def decode(code: String): Option[ScalaTarget] = {
    Artifact(code).map(_._2)
  }

  def scala(version: SemanticVersion) =
    ScalaTarget(scalaVersion = version,
                scalaJsVersion = None,
                scalaNativeVersion = None)

  def scalaJs(scalaVersion: SemanticVersion, scalaJsVersion: SemanticVersion) =
    ScalaTarget(scalaVersion = scalaVersion,
                scalaJsVersion = Some(scalaJsVersion),
                scalaNativeVersion = None)

  def scalaNative(version: SemanticVersion,
                  scalaNativeVersion: SemanticVersion) =
    ScalaTarget(scalaVersion = version,
                scalaJsVersion = None,
                scalaNativeVersion = Some(scalaNativeVersion))

  def split(target: Option[ScalaTarget]): (Option[ScalaTargetType],
                                           Option[String],
                                           Option[String],
                                           Option[String]) = {
    val targetType = target.map(_.targetType)
    val scalaVersion = target.map(_.scalaVersion.forceBinary.toString)
    val scalaJsVersion =
      target.flatMap(_.scalaJsVersion.map(_.forceBinary.toString))
    val scalaNativeVersion =
      target.flatMap(_.scalaNativeVersion.map(_.forceBinary.toString))

    (targetType, scalaVersion, scalaJsVersion, scalaNativeVersion)
  }
}
