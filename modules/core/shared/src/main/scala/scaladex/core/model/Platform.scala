package scaladex.core.model

sealed trait Platform {
  def label: String
  def isValid: Boolean
}

case object Jvm extends Platform {
  override def toString: String = "JVM"
  override def label: String = "jvm"
  override def isValid: Boolean = true
}

case class ScalaJs(version: SemanticVersion) extends Platform {
  override def toString: String = s"Scala.js $version"
  override def label: String = s"sjs${version.encode}"
  override def isValid: Boolean = ScalaJs.stableVersions.contains(this)
}

object ScalaJs {
  val `0.6`: ScalaJs = ScalaJs(MinorVersion(0, 6))
  val `1.x`: ScalaJs = ScalaJs(MajorVersion(1))

  val stableVersions: Set[ScalaJs] = Set(`0.6`, `1.x`)
}

case class SbtPlugin(version: SemanticVersion) extends Platform {
  override def toString: String = s"sbt $version"
  override def label: String = s"sbt${version.encode}"
  override def isValid: Boolean = SbtPlugin.stableVersions.contains(this)
}

object SbtPlugin {
  val `0.13`: SbtPlugin = SbtPlugin(MinorVersion(0, 13))
  val `1.0`: SbtPlugin = SbtPlugin(MinorVersion(1, 0))
  val stableVersions: Set[SbtPlugin] = Set(`0.13`, `1.0`)
}

case class ScalaNative(version: SemanticVersion) extends Platform {
  override def toString: String = s"Scala Native $version"
  override def label: String = s"native${version.encode}"
  override def isValid: Boolean = ScalaNative.stableVersions.contains(this)
}

object ScalaNative {
  val `0.3`: ScalaNative = ScalaNative(MinorVersion(0, 3))
  val `0.4`: ScalaNative = ScalaNative(MinorVersion(0, 4))

  val stableVersions: Set[ScalaNative] = Set(`0.3`, `0.4`)
}

case class MillPlugin(version: SemanticVersion) extends Platform {
  override def toString: String = s"Mill $version"

  override def label: String = s"mill${version.encode}"

  override def isValid: Boolean = true
}

object MillPlugin {
  val `0.10` = MillPlugin(MinorVersion(0, 10))
}

object Platform {
  implicit val ordering: Ordering[Platform] = Ordering.by {
    case Jvm                  => (true, None, None, None, None)
    case ScalaJs(version)     => (false, Some(version), None, None, None)
    case ScalaNative(version) => (false, None, Some(version), None, None)
    case SbtPlugin(version)   => (false, None, None, Some(version), None)
    case MillPlugin(version)  => (false, None, None, None, Some(version))
  }

  def fromLabel(input: String): Option[Platform] =
    input match {
      case "jvm"             => Some(Jvm)
      case s"sjs$version"    => SemanticVersion.parse(version).map(ScalaJs.apply)
      case s"native$version" => SemanticVersion.parse(version).map(ScalaNative.apply)
      case s"sbt$version"    => SemanticVersion.parse(version).map(SbtPlugin.apply)
      case s"mill$version"   => SemanticVersion.parse(version).map(MillPlugin.apply)
      case _                 => None
    }
}
