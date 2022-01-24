package scaladex.core.model

import java.time.Instant

sealed trait GithubStatus {
  val updateDate: Instant

  override def toString: String = this match {
    case GithubStatus.Ok(updateDate)      => s"GithubStatus.Ok($updateDate)"
    case GithubStatus.Unknown(updateDate) => s"GithubStatus.Unkhown($updateDate)"
    case GithubStatus.Moved(updateDate, projectRef) =>
      s"GithubStatus.Moved($updateDate, newName = $projectRef)"
    case GithubStatus.NotFound(updateDate) => s"GithubStatus.NotFound($updateDate)"
    case GithubStatus.Failed(updateDate, errorCode, errorMessage) =>
      s"GithubStatus.Failed($updateDate, code = $errorCode, reason = $errorMessage)"
  }

  def moved: Boolean = this match {
    case GithubStatus.Moved(_, _) => true
    case _                        => false
  }

  def notFound: Boolean = this match {
    case GithubStatus.NotFound(_) => true
    case _                        => false
  }
}
object GithubStatus {
  case class Ok(updateDate: Instant) extends GithubStatus
  case class Unknown(updateDate: Instant) extends GithubStatus
  case class Moved(updateDate: Instant, destination: Project.Reference) extends GithubStatus
  case class NotFound(updateDate: Instant) extends GithubStatus
  case class Failed(updateDate: Instant, errorCode: Int, errorMessage: String) extends GithubStatus
}
