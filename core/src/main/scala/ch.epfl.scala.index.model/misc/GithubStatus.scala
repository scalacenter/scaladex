package ch.epfl.scala.index.model.misc

import java.time.Instant

import ch.epfl.scala.index.newModel.NewProject

sealed trait GithubStatus {
  val when: Instant

  override def toString: String = this match {
    case GithubStatus.Ok(when)      => s"GithubStatus.Ok($when)"
    case GithubStatus.Unkhown(when) => s"GithubStatus.Unkhown($when)"
    case GithubStatus.Moved(when, organization, repository) =>
      s"GithubStatus.Moved($when, newName = $organization/$repository)"
    case GithubStatus.NotFound(when) => s"GithubStatus.NotFound($when)"
    case GithubStatus.Failed(when, errorCode, errorMessage) =>
      s"GithubStatus.Failed($when, code = $errorCode, reason = $errorMessage)"
  }

  def movedOrNotFound: Boolean = this match {
    case GithubStatus.Ok(_)           => false
    case GithubStatus.Unkhown(_)      => false
    case GithubStatus.Moved(_, _, _)  => true
    case GithubStatus.NotFound(_)     => true
    case GithubStatus.Failed(_, _, _) => false
  }

}
object GithubStatus {
  case class Ok(when: Instant) extends GithubStatus
  case class Unkhown(when: Instant) extends GithubStatus
  case class Moved(when: Instant, organization: NewProject.Organization, repository: NewProject.Repository)
      extends GithubStatus
  case class NotFound(when: Instant) extends GithubStatus
  case class Failed(when: Instant, errorCode: Int, errorMessage: String) extends GithubStatus
}
