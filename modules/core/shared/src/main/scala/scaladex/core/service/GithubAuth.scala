package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.UserState

trait GithubAuth {
  def getUserStateWithToken(token: String): Future[UserState]
  def getUserStateWithOauth2(code: String): Future[UserState]
}
