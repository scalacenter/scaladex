package ch.epfl.scala.index.model.misc

import java.time.Instant

import ch.epfl.scala.index.newModel.Project

sealed trait GithubStatus {
  val when: Instant

  override def toString: String = this match {
    case GithubStatus.Ok(when)      => s"GithubStatus.Ok($when)"
    case GithubStatus.Unknown(when) => s"GithubStatus.Unkhown($when)"
    case GithubStatus.Moved(when, organization, repository) =>
      s"GithubStatus.Moved($when, newName = $organization/$repository)"
    case GithubStatus.NotFound(when) => s"GithubStatus.NotFound($when)"
    case GithubStatus.Failed(when, errorCode, errorMessage) =>
      s"GithubStatus.Failed($when, code = $errorCode, reason = $errorMessage)"
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
  case class Ok(when: Instant) extends GithubStatus
  case class Unknown(when: Instant) extends GithubStatus
  case class Moved(when: Instant, organization: Project.Organization, repository: Project.Repository)
      extends GithubStatus
  case class NotFound(when: Instant) extends GithubStatus
  case class Failed(when: Instant, errorCode: Int, errorMessage: String) extends GithubStatus
}