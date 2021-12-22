package ch.epfl.scala.index.model.misc

import java.time.Instant

import ch.epfl.scala.index.newModel.Project

sealed trait GithubStatus {
  val update: Instant

  override def toString: String = this match {
    case GithubStatus.Ok(update)      => s"GithubStatus.Ok($update)"
    case GithubStatus.Unknown(update) => s"GithubStatus.Unkhown($update)"
    case GithubStatus.Moved(update, organization, repository) =>
      s"GithubStatus.Moved($update, newName = $organization/$repository)"
    case GithubStatus.NotFound(update) => s"GithubStatus.NotFound($update)"
    case GithubStatus.Failed(update, errorCode, errorMessage) =>
      s"GithubStatus.Failed($update, code = $errorCode, reason = $errorMessage)"
  }

  def movedOrNotFound: Boolean = this match {
    case GithubStatus.Ok(_)           => false
    case GithubStatus.Unknown(_)      => false
    case GithubStatus.Moved(_, _, _)  => true
    case GithubStatus.NotFound(_)     => true
    case GithubStatus.Failed(_, _, _) => false
  }

}
object GithubStatus {
  case class Ok(update: Instant) extends GithubStatus
  case class Unknown(update: Instant) extends GithubStatus
  case class Moved(update: Instant, organization: Project.Organization, repository: Project.Repository)
      extends GithubStatus
  case class NotFound(update: Instant) extends GithubStatus
  case class Failed(update: Instant, errorCode: Int, errorMessage: String) extends GithubStatus
}
