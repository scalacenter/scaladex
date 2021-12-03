package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubRepo

trait GithubService {
  def getReadme(repo: GithubRepo): Future[String]
  def update(repo: GithubRepo): Future[GithubInfo]
}
