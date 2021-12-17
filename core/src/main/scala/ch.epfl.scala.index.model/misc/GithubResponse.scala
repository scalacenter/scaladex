package ch.epfl.scala.index.model.misc

sealed trait GithubResponse[+T]

object GithubResponse {
  case class Ok[+T](res: T) extends GithubResponse[T]
  case class MovedPermanently[+T](res: T) extends GithubResponse[T]
  case class Failed(code: Int, errorMessage: String) extends GithubResponse[Nothing]
}
