package ch.epfl.scala.index.model
package release

sealed trait ScalaTargetType

object ScalaTargetType {
  val All: Seq[ScalaTargetType] = Seq(Java, Sbt, Native, Js, Jvm)

  implicit val ordering: Ordering[ScalaTargetType] = Ordering.by(All.indexOf(_))

  def ofName(name: String): Option[ScalaTargetType] =
    All.find(_.getClass.getSimpleName.stripSuffix("$").equalsIgnoreCase(name))

  case object Java extends ScalaTargetType
  case object Sbt extends ScalaTargetType
  case object Native extends ScalaTargetType
  case object Js extends ScalaTargetType
  case object Jvm extends ScalaTargetType
}
