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

  implicit val ordering: Ordering[ScalaJs] = Ordering.by(p => p.asInstanceOf[Platform])
}

case class SbtPlugin(version: SemanticVersion) extends Platform {
  override def toString: String =
    version match {
      case MinorVersion(1, 0) => s"sbt 1.x"
      case _                  => s"sbt $version"
    }
  override def label: String = s"sbt${version.encode}"
  override def isValid: Boolean = SbtPlugin.stableVersions.contains(this)
}

object SbtPlugin {
  val `0.13`: SbtPlugin = SbtPlugin(MinorVersion(0, 13))
  val `1.0`: SbtPlugin = SbtPlugin(MinorVersion(1, 0))
  val stableVersions: Set[SbtPlugin] = Set(`0.13`, `1.0`)

  implicit val ordering: Ordering[SbtPlugin] = Ordering.by(p => p.asInstanceOf[Platform])
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

  implicit val ordering: Ordering[ScalaNative] = Ordering.by(p => p.asInstanceOf[Platform])
}

case class MillPlugin(version: SemanticVersion) extends Platform {
  override def toString: String = s"Mill $version"

  override def label: String = s"mill${version.encode}"

  override def isValid: Boolean = version match {
    case MinorVersion(_, _) => true
    case _                  => false
  }
}

object MillPlugin {
  val `0.10` = MillPlugin(MinorVersion(0, 10))

  implicit val ordering: Ordering[MillPlugin] = Ordering.by(p => p.asInstanceOf[Platform])
}

object Platform {
  implicit val ordering: Ordering[Platform] = Ordering.by {
    case Jvm                  => (5, None)
    case ScalaJs(version)     => (4, Some(version))
    case ScalaNative(version) => (3, Some(version))
    case SbtPlugin(version)   => (2, Some(version))
    case MillPlugin(version)  => (1, Some(version))
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
