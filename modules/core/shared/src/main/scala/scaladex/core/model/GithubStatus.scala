package scaladex.core.model

import java.time.Instant

sealed trait GithubStatus extends Ordered[GithubStatus] {
  val updateDate: Instant

  def isOk: Boolean = this match {
    case GithubStatus.Ok(_) => true
    case _                  => false
  }

  def isMoved: Boolean = this match {
    case GithubStatus.Moved(_, _) => true
    case _                        => false
  }

  def isNotFound: Boolean = this match {
    case GithubStatus.NotFound(_) => true
    case _                        => false
  }

  def isFailed: Boolean = this match {
    case GithubStatus.Failed(_, _, _) => true
    case _                            => false
  }

  override def compare(that: GithubStatus): Int =
    GithubStatus.ordering.compare(this, that)

}
object GithubStatus {
  case class Ok(updateDate: Instant) extends GithubStatus
  case class Unknown(updateDate: Instant) extends GithubStatus
  case class Moved(updateDate: Instant, destination: Project.Reference) extends GithubStatus
  case class NotFound(updateDate: Instant) extends GithubStatus
  case class Failed(updateDate: Instant, errorCode: Int, errorMessage: String) extends GithubStatus

  implicit val ordering: Ordering[GithubStatus] = Ordering.by {
    case GithubStatus.Unknown(_)    => Instant.MIN
    case githubStatus: GithubStatus => githubStatus.updateDate
  }
}
