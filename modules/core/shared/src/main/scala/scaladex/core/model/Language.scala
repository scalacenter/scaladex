package scaladex.core.model

sealed trait Language:
  // a string value for serialization/deserialization
  def value: String
  // a short version of toString
  def label: String
  def isValid: Boolean
  def isJava: Boolean = this match
    case Java => true
    case _ => false
  def isScala: Boolean = this match
    case Scala(_) => true
    case _ => false
end Language

object Language:
  implicit val ordering: Ordering[Language] = Ordering.by {
    case Java => Version(Int.MinValue)
    case Scala(v) => v
  }

  def parse(value: String): Option[Language] = value match
    case "java" => Some(Java)
    case sv => Version.parseSemantically(sv).map(Scala.apply)

case object Java extends Language:
  override def value: String = "java"
  override def label: String = toString
  override def isValid: Boolean = true
  override def toString: String = "Java"

final case class Scala(version: Version) extends Language:
  override def value: String = version.value
  override def label: String = version.toString
  override def isValid: Boolean = Scala.stableVersions.contains(this)
  override def toString: String = s"Scala $version"

object Scala:
  val `2.10`: Scala = Scala(Version(2, 10))
  val `2.11`: Scala = Scala(Version(2, 11))
  val `2.12`: Scala = Scala(Version(2, 12))
  val `2.13`: Scala = Scala(Version(2, 13))
  val `3`: Scala = Scala(Version(3))

  val stableVersions: Set[Scala] = Set(`2.10`, `2.11`, `2.12`, `2.13`, `3`)

  def fromFullVersion(fullVersion: Version): Scala =
    val binaryVersion = fullVersion match
      case Version.SemanticLike(major, Some(minor), _, _, _, _) =>
        if major > 3 then Version(major) else Version(major, minor)
      case _ => fullVersion
    Scala(binaryVersion)

  implicit val ordering: Ordering[Scala] = Ordering.by(_.version)
end Scala
