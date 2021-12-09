package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.model.misc.UserInfo
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.utils.Secret

trait GithubService {
  val isScaladexTokenProvided: Boolean
  def getReadme(repo: GithubRepo): Future[String]
  def update(repo: GithubRepo): Future[GithubInfo]
  def fetchUser(userToken: Secret): Future[UserInfo]
  def fetchUserOrganizations(userToken: Secret): Future[Set[NewProject.Organization]]
  def fetchUserRepo(userToken: Secret, permissions: Seq[String]): Future[Seq[GithubRepo]]
}
