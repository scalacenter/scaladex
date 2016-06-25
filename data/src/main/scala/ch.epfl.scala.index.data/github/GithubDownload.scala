package ch.epfl.scala.index
package data
package github

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import ch.epfl.scala.index.data.download.PlayWsDownloader
import cleanup.GithubRepoExtractor
import maven.PomsReader
import model.misc.{GithubRepo, Url}

import scala.util.Success
import org.json4s._
import org.json4s.native.JsonMethods._
import native.Serialization.writePretty
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws.{WSRequest, WSResponse}

class GithubDownload(implicit system: ActorSystem, implicit val materializer: ActorMaterializer) extends PlayWsDownloader {

  implicit val formats = DefaultFormats
  implicit val serialization = native.Serialization

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
   * @param request the current request
   * @return
   */
  def applyBasicHeaders(request: WSRequest): WSRequest = request.withHeaders("Authorization" -> s"token $credentials")

  /**
   * basic Authentication header + Accept application jsob
   * @param request
   * @return the current request
   */
  def applyAcceptJsonHeaders(request: WSRequest): WSRequest = applyBasicHeaders(
    request.withHeaders("Accept" -> "application/json")
  )

  /**
   * Apply github authentication strategy and set response header to html
   * @param request the current request
   * @return
   */
  def applyReadmeHeaders(request: WSRequest): WSRequest = {

    applyBasicHeaders(request.withHeaders("Accept" -> "application/vnd.github.VERSION.html"))
  }

  /**
   * Save the json response to directory
   * @param filePath the file path to save the file
   * @param repo the current repo
   * @param response the response
   */
  private def saveJson(filePath: Path, repo: GithubRepo, response: WSResponse): Unit = {

    val dir = path(repo)
    Files.createDirectories(dir)

    Files.write(
      filePath,
      writePretty(parse(response.body)).getBytes(StandardCharsets.UTF_8)
    )
  }

  /**
   * Process the downloaded data from repository info
   * @param repo the current repo
   * @param response the response
   * @return
   */
  private def processInfoResponse(repo: GithubRepo, response: WSResponse): String = {

    saveJson(githubRepoInfoPath(repo), repo, response)
    response.body
  }

  /**
   * Process the downloaded collaborator from repository info
   * @param repo the current repo
   * @param response the response
   * @return
   */
  private def processCollaboratorResponse(repo: GithubRepo, response: WSResponse): String = {

    saveJson(githubRepoCollaboratorPath(repo), repo, response)
    response.body
  }

  /**
   * Process the downloaded issues data from repository info
   * @param repo the current repo
   * @param response the response
   * @return
   */
  private def processIssuesResponse(repo: GithubRepo, response: WSResponse): String = {

    saveJson(githubRepoIssuesPath(repo), repo, response)
    response.body
  }

  /**
   * Process the downloaded contributors data from repository info
   * @param repo the current repo
   * @param response the response
   * @return
   */
  private def processCointributorsResponse(repo: GithubRepo, response: WSResponse): String = {

    saveJson(githubRepoContributorsPath(repo), repo, response)
    response.body
  }

  /**
   * Process the downloaded data from repository info
   * @param repo the current repo
   * @param response the response
   * @return
   */
  private def processReadmeResponse(repo: GithubRepo, response: WSResponse): String = {

    val dir = path(repo)
    Files.createDirectories(dir)
    Files.write(
      githubReadmePath(repo),
      GithubReadme.absoluteUrl(response.body, repo, "master").getBytes(StandardCharsets.UTF_8)
    )

    response.body
  }

  /**
   * main github url to the api
   * @param repo the current github repo
   * @return
   */
  private def mainGithubUrl(repo: GithubRepo): String = s"https://api.github.com/repos/${repo.organization}/${repo.repo}"

  /**
   * get the Github Info url
   * @param repo the current repository
   * @return
   */
  private def githubInfoUrl(repo: GithubRepo) = Url(mainGithubUrl(repo))

  /**
   * get the Github readme url
   * @param repo the current repository
   * @return
   */
  private def githubReadmeUrl(repo: GithubRepo) = Url(mainGithubUrl(repo) + "/readme")

  /**
   * get the Github collaborators url
   * @param repo the current repository
   * @return
   */
  private def githubCollaboratorUrl(repo: GithubRepo) = Url(mainGithubUrl(repo) + "/collaborators")

  /**
   * get the Github issues url
   * @param repo the current repository
   * @return
   */
  private def githubIssuesUrl(repo: GithubRepo) = Url(mainGithubUrl(repo) + "/issues")

  /**
   * get the Github contributors url
   * @param repo the current repository
   * @return
   */
  private def githubContributorsUrl(repo: GithubRepo) = Url(mainGithubUrl(repo) + "/contributors")

  /**
   * process all downloads
   */
  def run() = {

    download[GithubRepo, String]("Downloading Repo Info", githubRepos, githubInfoUrl, applyAcceptJsonHeaders, processInfoResponse)
    download[GithubRepo, String]("Downloading Readme", githubRepos, githubReadmeUrl, applyReadmeHeaders, processReadmeResponse)
    /** todo: see https://github.com/scalacenter/scaladex/issues/115 comment 1 */
    // download[GithubRepo, String]("Downloading Collaborators", githubRepos, githubCollaboratorUrl, applyAcceptJsonHeaders, processCollaboratorResponse)
    download[GithubRepo, String]("Downloading Issues", githubRepos, githubIssuesUrl, applyAcceptJsonHeaders, processIssuesResponse)
    download[GithubRepo, String]("Downloading Contributors", githubRepos, githubContributorsUrl, applyAcceptJsonHeaders, processCointributorsResponse)
  }
}