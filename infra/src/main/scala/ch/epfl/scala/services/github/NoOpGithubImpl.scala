package ch.epfl.scala.services.github

import scala.concurrent.Future

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.services.GithubService

// this implementation doesn't connect to github
class NoOpGithubImpl extends GithubService {
  def getReadme(repo: GithubRepo): Future[String] = Future.failed(new Exception("github token not provided."))
  def update(repo: GithubRepo): Future[GithubInfo] = Future.failed(new Exception("github token not provided."))

}
