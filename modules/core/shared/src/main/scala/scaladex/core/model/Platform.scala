package scaladex.core.model

sealed trait Platform:
  def value: String
  def isValid: Boolean

case object Jvm extends Platform:
  override def toString: String = "JVM"
  override def value: String = "jvm"
  override def isValid: Boolean = true

case class ScalaJs(version: Version) extends Platform:
  override def toString: String = s"Scala.js $version"
  override def value: String = s"sjs${version.value}"
  override def isValid: Boolean = ScalaJs.stableVersions.contains(this)

object ScalaJs:
  val `0.6`: ScalaJs = ScalaJs(Version(0, 6))
  val `1.x`: ScalaJs = ScalaJs(Version(1))

  val stableVersions: Set[ScalaJs] = Set(`0.6`, `1.x`)

  given ordering: Ordering[ScalaJs] = Ordering.by(p => p.asInstanceOf[Platform])

case class SbtPlugin(version: Version) extends Platform:
  override def toString: String = s"sbt $version"
  override def value: String = s"sbt${version.value}"
  override def isValid: Boolean = SbtPlugin.stableVersions.contains(this) || isSbt2PreRelease

  private def isSbt2PreRelease: Boolean = version match
    case Version.SemanticLike(2, Some(0), Some(0), None, Some(_), None) => true
    case _ => false

object SbtPlugin:
  val `0.13`: SbtPlugin = SbtPlugin(Version(0, 13))
  val `1.x`: SbtPlugin = SbtPlugin(Version(1))
  val `2.x`: SbtPlugin = SbtPlugin(Version(2))

  def apply(version: Version): SbtPlugin = version match
    case Version.Minor(1, 0) => `1.x`
    case _ => new SbtPlugin(version)

  val stableVersions: Set[SbtPlugin] = Set(`0.13`, `1.x`, `2.x`)

  given ordering: Ordering[SbtPlugin] = Ordering.by(p => p.asInstanceOf[Platform])
end SbtPlugin

case class ScalaNative(version: Version) extends Platform:
  override def toString: String = s"Scala Native $version"
  override def value: String = s"native${version.value}"
  override def isValid: Boolean = ScalaNative.stableVersions.contains(this)

object ScalaNative:
  val `0.3`: ScalaNative = ScalaNative(Version(0, 3))
  val `0.4`: ScalaNative = ScalaNative(Version(0, 4))
  val `0.5`: ScalaNative = ScalaNative(Version(0, 5))

  val stableVersions: Set[ScalaNative] = Set(`0.3`, `0.4`, `0.5`)

  given ordering: Ordering[ScalaNative] = Ordering.by(p => p.asInstanceOf[Platform])

case class MillPlugin(version: Version) extends Platform:
  override def toString: String = s"Mill $version"

  override def value: String = s"mill${version.value}"

  override def isValid: Boolean = version match
    case Version.Major(_) | Version.Minor(_, _) => true
    case _ => false

object MillPlugin:
  val `0.10`: MillPlugin = MillPlugin(Version(0, 10))

  given ordering: Ordering[MillPlugin] = Ordering.by(p => p.asInstanceOf[Platform])

object Platform:
  given ordering: Ordering[Platform] = Ordering.by {
    case Jvm => (5, None)
    case ScalaJs(version) => (4, Some(version))
    case ScalaNative(version) => (3, Some(version))
    case SbtPlugin(version) => (2, Some(version))
    case MillPlugin(version) => (1, Some(version))
  }

  def parse(input: String): Option[Platform] =
    input match
      case "jvm" => Some(Jvm)
      case s"sjs$version" => Version.parseSemantically(version).map(ScalaJs.apply)
      case s"native$version" => Version.parseSemantically(version).map(ScalaNative.apply)
      case s"sbt$version" => Version.parseSemantically(version).map(SbtPlugin.apply)
      case s"mill$version" => Version.parseSemantically(version).map(MillPlugin.apply)
      case _ => None
end Platform
