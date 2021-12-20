package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubResponse
import ch.epfl.scala.index.model.misc.UserInfo
import ch.epfl.scala.index.newModel.NewProject

trait GithubService {
  def getReadme(ref: NewProject.Reference): Future[String]
  def update(ref: NewProject.Reference): Future[GithubResponse[GithubInfo]]
  def fetchUser(): Future[UserInfo]
  def fetchUserOrganizations(login: String): Future[Set[NewProject.Organization]]
  def fetchReposUnderUserOrganizations(login: String, filterPermissions: Seq[String]): Future[Seq[NewProject.Reference]]
  def fetchUserRepo(login: String, filterPermissions: Seq[String]): Future[Seq[NewProject.Reference]]
}
