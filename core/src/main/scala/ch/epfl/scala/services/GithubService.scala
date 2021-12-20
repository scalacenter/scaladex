package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubResponse
import ch.epfl.scala.index.model.misc.UserInfo
import ch.epfl.scala.index.newModel.Project

trait GithubService {
  def getReadme(ref: Project.Reference): Future[String]
  def update(ref: Project.Reference): Future[GithubResponse[GithubInfo]]
  def fetchUser(): Future[UserInfo]
  def fetchUserOrganizations(login: String): Future[Set[Project.Organization]]
  def fetchReposUnderUserOrganizations(login: String, filterPermissions: Seq[String]): Future[Seq[Project.Reference]]
  def fetchUserRepo(login: String, filterPermissions: Seq[String]): Future[Seq[Project.Reference]]
}
