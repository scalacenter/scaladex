package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.util.Secret

trait GithubAuth {
  def getToken(code: String): Future[Secret]
  def getUser(token: Secret): Future[UserInfo]
  def getUserState(token: Secret): Future[Option[UserState]]
}
