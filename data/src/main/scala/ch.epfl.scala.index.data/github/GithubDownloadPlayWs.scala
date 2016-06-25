package ch.epfl.scala.index
package data
package github

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import ch.epfl.scala.index.data.download.PlayWsDownloader
import ch.epfl.scala.index.data.maven.PomsReader
import cleanup.GithubRepoExtractor
import model.{GithubRepo, Url}

import scala.util.Success

//import maven.PomsReader
import org.json4s._
import org.json4s.native.JsonMethods._

import native.Serialization.writePretty

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import play.api.libs.ws.{WSRequest, WSResponse}

class GithubDownloadPlayWs(implicit system: ActorSystem, implicit val materializer: ActorMaterializer) extends PlayWsDownloader {

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
   * Apply github authentication strategy and set accept header to application/json
   * @param request the current request
   * @return
   */
  def applyInfoHeaders(request: WSRequest): WSRequest = {

    request.withHeaders("Authorization" -> s"token $credentials", "Accept" -> "application/json")
  }

  /**
   * Apply github authentication strategy and set response header to html
   * @param request the current request
   * @return
   */
  def applyReadmeHeaders(request: WSRequest): WSRequest = {

    request.withHeaders("Authorization" -> s"token $credentials", "Accept" -> "application/vnd.github.VERSION.html")
  }

  /**
   * Process the downloaded data from repository info
   * @param repo the current repo
   * @param response the response
   * @return
   */
  private def processInfoResponse(repo: GithubRepo, response: WSResponse): String = {

    val dir = path(repo)
    Files.createDirectories(dir)

    Files.write(
      githubRepoInfoPath(repo),
      writePretty(parse(response.body)).getBytes(StandardCharsets.UTF_8)
    )

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
      // repo info contains default branch
      GithubReadme.absoluteUrl(response.body, repo, "master").getBytes(StandardCharsets.UTF_8)
    )

    response.body
  }

  /**
   * get the Github Info url
   * @param repo the current repository
   * @return
   */
  private def githubInfoUrl(repo: GithubRepo) = Url(s"https://api.github.com/repos/${repo.organization}/${repo.repo}")

  /**
   * get the Github readme url
   * @param repo the current repository
   * @return
   */
  private def githubReadmeUrl(repo: GithubRepo) = Url(s"https://api.github.com/repos/${repo.organization}/${repo.repo}/readme")


  def run() = {

    download[GithubRepo, String]("Downloading Repo Infos", githubRepos, githubInfoUrl, applyInfoHeaders, processInfoResponse)
    download[GithubRepo, String]("Downloading Readme", githubRepos, githubReadmeUrl, applyReadmeHeaders, processReadmeResponse)
  }
}