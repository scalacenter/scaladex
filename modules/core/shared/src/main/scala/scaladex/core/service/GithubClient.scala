package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.Project
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.util.Secret

trait GithubClient:
  def getProjectInfo(ref: Project.Reference, token: Secret): Future[GithubResponse[(Project.Reference, GithubInfo)]]
  def getUserInfo(token: Secret): Future[GithubResponse[UserInfo]]
  def getUserState(token: Secret): Future[GithubResponse[UserState]]
  def getUserOrganizations(login: String, token: Secret): Future[Seq[Project.Organization]]
  def getUserRepositories(login: String, filterPermissions: Seq[String], token: Secret): Future[Seq[Project.Reference]]
  def getOrganizationRepositories(
      user: String,
      organization: Project.Organization,
      filterPermissions: Seq[String],
      token: Secret
  ): Future[Seq[Project.Reference]]
end GithubClient
