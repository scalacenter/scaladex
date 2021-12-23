package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.Project
import scaladex.core.model.UserInfo

trait GithubService {
  def update(ref: Project.Reference): Future[GithubResponse[GithubInfo]]
  def fetchUser(): Future[UserInfo]
  def fetchUserOrganizations(login: String): Future[Set[Project.Organization]]
  def fetchReposUnderUserOrganizations(login: String, filterPermissions: Seq[String]): Future[Seq[Project.Reference]]
  def fetchUserRepo(login: String, filterPermissions: Seq[String]): Future[Seq[Project.Reference]]
}
