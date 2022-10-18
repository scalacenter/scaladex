package scaladex.core.model

sealed trait Language {
  def label: String
  def isValid: Boolean
  def isJava: Boolean = this match {
    case Java => true
    case _    => false
  }
  def isScala: Boolean = this match {
    case Scala(_) => true
    case _        => false
  }
}

object Language {
  implicit val ordering: Ordering[Language] = Ordering.by {
    case Java     => MajorVersion(Int.MinValue)
    case Scala(v) => v
  }

  def fromLabel(label: String): Option[Language] = label match {
    case "Java"   => Some(Java)
    case s"$sv.x" => SemanticVersion.parse(sv).map(Scala.apply)
    case sv       => SemanticVersion.parse(sv).map(Scala.apply)
  }
}

case object Java extends Language {
  override def label: String = toString
  override def isValid: Boolean = true
}

final case class Scala(version: SemanticVersion) extends Language {
  override def label: String = version.toString
  override def isValid: Boolean = Scala.stableVersions.contains(this)
  override def toString: String = s"Scala $version"
}

object Scala {
  val `2.10`: Scala = Scala(MinorVersion(2, 10))
  val `2.11`: Scala = Scala(MinorVersion(2, 11))
  val `2.12`: Scala = Scala(MinorVersion(2, 12))
  val `2.13`: Scala = Scala(MinorVersion(2, 13))
  val `3`: Scala = Scala(MajorVersion(3))

  val stableVersions: Set[Scala] = Set(`2.10`, `2.11`, `2.12`, `2.13`, `3`)

  def fromFullVersion(fullVersion: SemanticVersion): Scala = {
    val binaryVersion = fullVersion match {
      case SemanticVersion(major, Some(minor), _, _, _, _) =>
        if (major > 3) MajorVersion(major) else MinorVersion(major, minor)
      case _ => fullVersion
    }
    Scala(binaryVersion)
  }

  implicit val ordering: Ordering[Scala] = Ordering.by(_.version)
}
