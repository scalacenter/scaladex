package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.Project
import scaladex.core.model.UserInfo

trait GithubService {
  def getProjectInfo(ref: Project.Reference): Future[GithubResponse[GithubInfo]]
  def getUserInfo(): Future[UserInfo]
  def getUserOrganizations(login: String): Future[Set[Project.Organization]]
  def getUserOrganizationRepositories(login: String, filterPermissions: Seq[String]): Future[Seq[Project.Reference]]
  def getUserRepositories(login: String, filterPermissions: Seq[String]): Future[Seq[Project.Reference]]
}
