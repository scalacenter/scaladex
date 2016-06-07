package ch.epfl.scala.index


package object model {
  import upickle.default._

  // speed up compilation / avoid File too long errors

  implicit val rwSemanticVersion = macroRW[SemanticVersion]
  implicit val rwScalaTargets = macroRW[ScalaTargets]
  implicit val rwMavenReference = macroRW[MavenReference]
  implicit val rwISO_8601_Date = macroRW[ISO_8601_Date]
  implicit val rwReleaseReference = macroRW[Release.Reference]
  implicit val rwRelease = macroRW[Release]
  

  implicit val rwArtifactReference = macroRW[Artifact.Reference]
  implicit val rwDeprecation = macroRW[Deprecation]
  implicit val rwArtifact = macroRW[Artifact]

  implicit val rwProjectReference = macroRW[Project.Reference]
  implicit val rwUrl = macroRW[Url]
  implicit val rwGithubRepo = macroRW[GithubRepo]
  implicit val rwProject = macroRW[Project]

  type PageIndex = Int
}