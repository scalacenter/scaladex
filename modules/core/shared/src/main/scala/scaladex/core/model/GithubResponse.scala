package scaladex.core.model

sealed trait GithubResponse[+T] {
  def isOk: Boolean = this match {
    case GithubResponse.Ok(_) => true
    case _                    => false
  }

  def isMoved: Boolean = this match {
    case GithubResponse.MovedPermanently(_) => true
    case _                                  => false
  }

  def isFailed: Boolean = this match {
    case GithubResponse.Failed(_, _) => true
    case _                           => false
  }
}

object GithubResponse {
  case class Ok[+T](res: T) extends GithubResponse[T]
  case class MovedPermanently[+T](res: T) extends GithubResponse[T]
  case class Failed(code: Int, errorMessage: String) extends GithubResponse[Nothing]
}
