package ch.epfl.scala.index
package data
package github

import download.PlayWsDownloader
import cleanup.GithubRepoExtractor
import maven.PomsReader
import model.misc.GithubRepo

import org.json4s._
import native.JsonMethods._
import native.Serialization._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import play.api.libs.ws._
import play.api.libs.ws.ahc.AhcWSClient

import scala.util._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class GithubDownload(privateCredentials: Option[GithubCredentials] = None)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer)
    extends PlayWsDownloader {

  import Json4s._

  case class PaginatedGithub(repo: GithubRepo, page: Int)

  private lazy val githubRepoExtractor = new GithubRepoExtractor
  private lazy val githubRepos = {
    PomsReader
      .load()
      .collect { case Success((pom, _)) => githubRepoExtractor(pom) }
      .flatten
      .toSet
  }
  private lazy val paginatedGithubRepos =
    githubRepos.map(repo => PaginatedGithub(repo, 1))

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
  def applyBasicHeaders(request: WSRequest): WSRequest = {

    privateCredentials
      .map(cred =>
            request.withAuth(cred.username, cred.password, WSAuthScheme.BASIC))
      .getOrElse(request.withHeaders("Authorization" -> s"token $credentials"))
  }

  /**
    * basic Authentication header + Accept application jsob
    *
    * @param request
    * @return the current request
    */
  def applyAcceptJsonHeaders(request: WSRequest): WSRequest =
    applyBasicHeaders(
        request.withHeaders("Accept" -> "application/json")
    )

  /**
    * Apply github authentication strategy and set response header to html
    *
    * @param request the current request
    * @return
    */
  def applyReadmeHeaders(request: WSRequest): WSRequest = {

    applyBasicHeaders(
        request.withHeaders("Accept" -> "application/vnd.github.VERSION.html"))
  }

  /**
    * Save the json response to directory
    *
    * @param filePath the file path to save the file
    * @param repo the current repo
    * @param data the response data
    */
  private def saveJson(filePath: Path, repo: GithubRepo, data: String): Path = {

    val dir = path(repo)
    Files.createDirectories(dir)

    Files.write(
        filePath,
        writePretty(parse(data)).getBytes(StandardCharsets.UTF_8)
    )
  }

  /**
    * Process the downloaded data from repository info
    *
    * @param repo the current repo
    * @param response the response
    * @return
    */
  private def processInfoResponse(repo: GithubRepo,
                                  response: WSResponse): Unit = {

    if (200 == response.status) {

      saveJson(githubRepoInfoPath(repo), repo, response.body)
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
  private def processIssuesResponse(repo: GithubRepo,
                                    response: WSResponse): Unit = {

    if (200 == response.status) {

      saveJson(githubRepoIssuesPath(repo), repo, response.body)
    }

    ()
  }

  /**
    * Convert contributor response to a List of Contributors
    *
    * @param repo
    * @param response
    * @return
    */
  private def convertContributorResponse(
      repo: PaginatedGithub,
      response: WSResponse): List[Contributor] = {

    Try(read[List[Contributor]](response.body)) match {

      case Success(contributors) => contributors
      case Failure(ex)           => List()
    }
  }

  /**
    * Process the downloaded contributors data from repository info
    *
    * @param repo the current repo
    * @param response the response
    * @return
    */
  private def processContributorResponse(repo: PaginatedGithub,
                                         response: WSResponse): Unit = {

    /*
     * Github api contributor response is just a list of 30 entries, to make sure we get
     * all, wee need to extract the "Link" header, count all pages (there is a List rel)
     * and download all pages. These pages are converted to a List[Contributor] and
     * get returned. if there is only one page, return an empty list
     */
    def downloadAllPages: List[Contributor] = {

      val lastPage = response.header("Link").map(extractLastPage).getOrElse(1)

      if (1 == repo.page && 1 < lastPage) {

        val pages = List
          .range(2, lastPage + 1)
          .map(page => PaginatedGithub(repo.repo, page))
          .toSet

        val pagedContributors = download[PaginatedGithub, List[Contributor]](
            "Download contributor Pages",
            pages,
            githubContributorsUrl,
            convertContributorResponse
        )

        pagedContributors.flatten.toList
      } else List()
    }

    if (200 == response.status) {

      /* current contributors + all other pages in  amount of contributions order */
      val contributors =
        (convertContributorResponse(repo, response) ++ downloadAllPages)
          .sortBy(_.contributions)
          .reverse

      saveJson(githubRepoContributorsPath(repo.repo),
               repo.repo,
               writePretty(contributors))
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
  private def processReadmeResponse(repo: GithubRepo,
                                    response: WSResponse): Unit = {

    if (200 == response.status) {

      val dir = path(repo)
      Files.createDirectories(dir)
      Files.write(
          githubReadmePath(repo),
          GithubReadme
            .absoluteUrl(response.body, repo, "master")
            .getBytes(StandardCharsets.UTF_8)
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
  private def mainGithubUrl(repo: GithubRepo): String =
    s"https://api.github.com/repos/${repo.organization}/${repo.repository}"

  /**
    * get the Github Info url
    *
    * @param repo the current repository
    * @return
    */
  private def githubInfoUrl(wsClient: AhcWSClient,
                            repo: GithubRepo): WSRequest = {

    applyAcceptJsonHeaders(wsClient.url(mainGithubUrl(repo)))
  }

  /**
    * get the Github readme url
    *
    * @param repo the current repository
    * @return
    */
  private def githubReadmeUrl(wsClient: AhcWSClient,
                              repo: GithubRepo): WSRequest = {

    applyReadmeHeaders(wsClient.url(mainGithubUrl(repo) + "/readme"))
  }

  /**
    * get the Github issues url
    *
    * @param repo the current repository
    * @return
    */
  private def githubIssuesUrl(wsClient: AhcWSClient,
                              repo: GithubRepo): WSRequest = {

    applyAcceptJsonHeaders(wsClient.url(mainGithubUrl(repo) + "/issues"))
  }

  /**
    * get the Github contributors url
    *
    * @param repo the current repository
    * @return
    */
  private def githubContributorsUrl(wsClient: AhcWSClient,
                                    repo: PaginatedGithub): WSRequest = {

    applyAcceptJsonHeaders(
        wsClient.url(
            mainGithubUrl(repo.repo) + s"/contributors?page=${repo.page}"))
  }

  /**
    * process all downloads
    */
  def run(): Unit = {

    download[GithubRepo, Unit]("Downloading Repo Info",
                               githubRepos,
                               githubInfoUrl,
                               processInfoResponse)
    download[GithubRepo, Unit]("Downloading Readme",
                               githubRepos,
                               githubReadmeUrl,
                               processReadmeResponse)
    // todo: for later @see #112 - remember that issues are paginated - see contributors */
    // download[GithubRepo, Unit]("Downloading Issues", githubRepos, githubIssuesUrl, processIssuesResponse)
    download[PaginatedGithub, Unit]("Downloading Contributors",
                                    paginatedGithubRepos,
                                    githubContributorsUrl,
                                    processContributorResponse)

    ()
  }

  /**
    * Download github info for specific repository
    *
    * @param repo the github repository
    * @param info flag if info can be downloaded
    * @param readme flag if readme can be downloaded
    * @param contributors flag if contributors can be downloaded
    */
  def run(repo: GithubRepo,
          info: Boolean,
          readme: Boolean,
          contributors: Boolean): Unit = {

    if (info) {

      download[GithubRepo, Unit]("Downloading Repo Info",
                                 Set(repo),
                                 githubInfoUrl,
                                 processInfoResponse)
    }

    if (readme) {

      download[GithubRepo, Unit]("Downloading Readme",
                                 Set(repo),
                                 githubReadmeUrl,
                                 processReadmeResponse)
    }

    if (contributors) {

      val paginated = Set(PaginatedGithub(repo, 1))
      download[PaginatedGithub, Unit]("Downloading Contributors",
                                      paginated,
                                      githubContributorsUrl,
                                      processContributorResponse)
    }

    ()
  }

}
