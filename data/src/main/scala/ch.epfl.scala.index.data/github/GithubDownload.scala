package ch.epfl.scala.index
package data
package github

import download.PlayWsDownloader
import cleanup.GithubRepoExtractor
import maven.PomsReader
import model.misc.GithubRepo

import org.json4s._
import native.JsonMethods._
import native.Serialization.writePretty

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import play.api.libs.ws.{WSRequest, WSResponse}

import scala.util.Success
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class GithubDownload(implicit val system: ActorSystem, implicit val materializer: ActorMaterializer) extends PlayWsDownloader {

  import Json4s._

  private val githubRepoExtractor = new GithubRepoExtractor
  private val githubRepos = {
    PomsReader.load()
      .collect { case Success((pom, _)) => githubRepoExtractor(pom) }
      .flatten
      .map { case GithubRepo(owner, repo) => GithubRepo(owner.toLowerCase, repo.toLowerCase) }
      .toSet
  }

  private def credentials = {
    val tokens = Array(
      "5e2ddeed0f9c6169d868121330599b8353ab0b55",
      "6e7364f7db333be44b5aa0416f5a0b33d8743b14",
      "6518021b0dbf82757717c9793906425adc779eb3"
    )
    tokens(scala.util.Random.nextInt(tokens.length))
  }

  /**
   * Apply github authentication strategy
   *
   * @param request the current request
   * @return
   */
  def applyBasicHeaders(request: WSRequest): WSRequest = request.withHeaders("Authorization" -> s"token $credentials")

  /**
   * basic Authentication header + Accept application jsob
   *
   * @param request
   * @return the current request
   */
  def applyAcceptJsonHeaders(request: WSRequest): WSRequest = applyBasicHeaders(

    request.withHeaders("Accept" -> "application/json")
  )

  /**
   * Apply github authentication strategy and set response header to html
   *
   * @param request the current request
   * @return
   */
  def applyReadmeHeaders(request: WSRequest): WSRequest = {

    applyBasicHeaders(request.withHeaders("Accept" -> "application/vnd.github.VERSION.html"))
  }

  /**
   * Save the json response to directory
   *
   * @param filePath the file path to save the file
   * @param repo the current repo
   * @param response the response
   */
  private def saveJson(filePath: Path, repo: GithubRepo, response: WSResponse): Path = {

    val dir = path(repo)
    Files.createDirectories(dir)

    Files.write(
      filePath,
      writePretty(parse(response.body)).getBytes(StandardCharsets.UTF_8)
    )
  }

  /**
   * Process the downloaded data from repository info
   *
   * @param repo the current repo
   * @param response the response
   * @return
   */
  private def processInfoResponse(repo: GithubRepo, response: WSResponse): Unit = {

    if (200 == response.status) {

      saveJson(githubRepoInfoPath(repo), repo, response)
    }

    ()
  }

  /**
   * Process the downloaded issues data from repository info
   *
   * @param repo the current repo
   * @param response the response
   * @return
   */
  private def processIssuesResponse(repo: GithubRepo, response: WSResponse): Unit = {

    if (200 == response.status) {

      saveJson(githubRepoIssuesPath(repo), repo, response)
    }

    ()
  }

  /**
   * Process the downloaded contributors data from repository info
   *
   * @param repo the current repo
   * @param response the response
   * @return
   */
  private def processContributorResponse(repo: GithubRepo, response: WSResponse): Unit = {

    if (200 == response.status) {

      saveJson(githubRepoContributorsPath(repo), repo, response)
    }

    ()
  }

  /**
   * Process the downloaded data from repository info
   *
   * @param repo the current repo
   * @param response the response
   * @return
   */
  private def processReadmeResponse(repo: GithubRepo, response: WSResponse): Unit = {

    if (200 == response.status) {

      val dir = path(repo)
      Files.createDirectories(dir)
      Files.write(
        githubReadmePath(repo),
        GithubReadme.absoluteUrl(response.body, repo, "master").getBytes(StandardCharsets.UTF_8)
      )
    }
    
    ()
  }

  /**
   * main github url to the api
   *
   * @param repo the current github repo
   * @return
   */
  private def mainGithubUrl(repo: GithubRepo): String = s"https://api.github.com/repos/${repo.organization}/${repo.repo}"

  /**
   * get the Github Info url
   *
   * @param repo the current repository
   * @return
   */
  private def githubInfoUrl(repo: GithubRepo): WSRequest = {

    applyAcceptJsonHeaders(wsClient.url(mainGithubUrl(repo)))
  }

  /**
   * get the Github readme url
   *
   * @param repo the current repository
   * @return
   */
  private def githubReadmeUrl(repo: GithubRepo): WSRequest = {

    applyReadmeHeaders(wsClient.url(mainGithubUrl(repo) + "/readme"))
  }

  /**
   * get the Github issues url
   *
   * @param repo the current repository
   * @return
   */
  private def githubIssuesUrl(repo: GithubRepo): WSRequest = {

    applyAcceptJsonHeaders(wsClient.url(mainGithubUrl(repo) + "/issues"))
  }

  /**
   * get the Github contributors url
   *
   * @param repo the current repository
   * @return
   */
  private def githubContributorsUrl(repo: GithubRepo): WSRequest = {

    applyAcceptJsonHeaders(wsClient.url(mainGithubUrl(repo) + "/contributors"))
  }

  /**
   * process all downloads
   */
  def run(): Unit = {

    download[GithubRepo, Unit]("Downloading Repo Info", githubRepos, githubInfoUrl, processInfoResponse)
    download[GithubRepo, Unit]("Downloading Readme", githubRepos, githubReadmeUrl, processReadmeResponse)
    // todo: for later @see #112 */
    // download[GithubRepo, Unit]("Downloading Issues", githubRepos, githubIssuesUrl, processIssuesResponse)
    download[GithubRepo, Unit]("Downloading Contributors", githubRepos, githubContributorsUrl, processContributorResponse)

    ()
  }
}