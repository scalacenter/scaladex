package ch.epfl.scala.index

package object model {
  import upickle.default._
  // speed up compilation / avoid File too long errors
  implicit val rwProject = macroRW[Project]
  implicit val rwArtifact = macroRW[Artifact]
  implicit val rwRelease = macroRW[Release]
}