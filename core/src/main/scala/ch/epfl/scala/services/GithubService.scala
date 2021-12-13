package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.model.misc.UserInfo
import ch.epfl.scala.index.newModel.NewProject

trait GithubService {
  def getReadme(repo: GithubRepo): Future[String]
  def update(repo: GithubRepo): Future[GithubInfo]
  def fetchUser(): Future[UserInfo]
  def fetchUserOrganizations(): Future[Set[NewProject.Organization]]
  def fetchReposUnderUserOrganizations(filterPermissions: Seq[String]): Future[Seq[GithubRepo]]
  def fetchUserRepo(filterPermissions: Seq[String]): Future[Seq[GithubRepo]]
}
