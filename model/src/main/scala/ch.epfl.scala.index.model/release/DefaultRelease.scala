package ch.epfl.scala.index.model
package release

case class ReleaseSelection(
  artifact: Option[String] = None,
  version: Option[SemanticVersion] = None,
  target: Option[ScalaTarget] = None
)

case class ReleaseOptions(
  artifacts: List[String],
  versions: List[SemanticVersion],
  targets: List[ScalaTarget],
  release: Release
)

object DefaultRelease {
  def apply(project: Project, selection: ReleaseSelection, releases: List[Release]): Option[ReleaseOptions] = {

    None
  }
  private def finds[A, B](xs: List[(A, B)], fs: List[A => Boolean]): Option[(A, B)] = {
    fs match {
      case Nil => None
      case f :: h =>
        xs.find{ case (a, b) => f(a) } match {
          case None => finds(xs, h)
          case s    => s
        }
    }
  }
}