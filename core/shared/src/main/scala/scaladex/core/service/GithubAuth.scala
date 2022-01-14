package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.UserState

trait GithubAuth {
  def getUserStateWithToken(token: String): Future[UserState]

  def getUserStateWithOauth2(
      clientId: String,
      clientSecret: String,
      code: String,
      redirectUri: String
  ): Future[UserState]
}
