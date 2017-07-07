package ch.epfl.scala.index
package data
package github

import download.PlayWsDownloader
import cleanup.GithubRepoExtractor
import maven.PomsReader
import model.misc.GithubRepo
import model.Project

import org.json4s._
import org.joda.time.DateTime
import native.JsonMethods._
import native.Serialization._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import play.api.libs.ws._
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.json._

import scala.util._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

class GithubDownload(paths: DataPaths,
                     privateCredentials: Option[GithubCredentials] = None)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer)
    extends PlayWsDownloader {

  private val log = LoggerFactory.getLogger(getClass)

  import Json4s._

  case class PaginatedGithub(repo: GithubRepo, page: Int)

  private lazy val githubRepoExtractor = new GithubRepoExtractor(paths)

  private lazy val githubRepos =
    PomsReader
      .loadAll(paths)
      .collect { case Success((pom, _, _)) => githubRepoExtractor(pom) }
      .flatten
      .toSet

  private lazy val paginatedGithubRepos =
    githubRepos.map(repo => PaginatedGithub(repo, 1))

  private val config =
    ConfigFactory.load().getConfig("org.scala_lang.index.data")

  private val credential =
    if (config.hasPath("github")) config.getString("github")
    else sys.error("Setup your GitHub token see CONTRIBUTING.md#GitHub")

  /**
    * Apply github authentication strategy
    *
    * @param request the current request
    * @return
    */
  def applyBasicHeaders(request: WSRequest): WSRequest = {
    val token =
      privateCredentials
        .map(_.token)
        .getOrElse(credential)

    request.addHttpHeaders("Authorization" -> s"bearer $token")
  }

  /**
    * basic Authentication header + Accept application jsob
    *
    * @param request
    * @return the current request
    */
  def applyAcceptJsonHeaders(request: WSRequest): WSRequest =
    applyBasicHeaders(
      request.addHttpHeaders("Accept" -> "application/json")
    )

  /**
    * Apply github authentication strategy and set response header to html
    *
    * @param request the current request
    * @return
    */
  def applyReadmeHeaders(request: WSRequest): WSRequest = {
    applyBasicHeaders(
      request.addHttpHeaders(
        "Accept" -> "application/vnd.github.VERSION.html"
      )
    )
  }

  /**
    * Apply github authentication strategy and set response header to html
    *
    * @param request the current request
    * @return
    */
  def applyCommunityProfileHeaders(request: WSRequest): WSRequest = {

    applyBasicHeaders(
      request.addHttpHeaders(
        "Accept" -> "application/vnd.github.black-panther-preview+json"))
  }

  /**
    * Save the json response to directory
    *
    * @param filePath the file path to save the file
    * @param repo the current repo
    * @param data the response data
    */
  private def saveJson(filePath: Path, repo: GithubRepo, data: String): Path = {

    val dir = path(paths, repo)
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
                                  response: WSResponse): Try[Unit] = {

    checkGithubApiError("Processing Info", response)
    .map(_ => {
      if (200 == response.status) {

      saveJson(githubRepoInfoPath(paths, repo), repo, response.body)
      GithubReader.appendMovedRepository(paths, repo)
    }

      ()
    })

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
      response: WSResponse): Try[List[Contributor]] = {

    checkGithubApiError("Converting Contributors", response)
    .map(_ => {
      if (200 == response.status) {

        read[List[Contributor]](response.body)
      } else {
        List()
      }
    })
  }

  /**
    * Process the downloaded contributors data from repository info
    *
    * @param repo the current repo
    * @param response the response
    * @return
    */
  private def processContributorResponse(repo: PaginatedGithub,
                                         response: WSResponse): Try[Unit] = {

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

        val pagedContributors = downloadGithub[PaginatedGithub, List[Contributor]](
          "Download contributor Pages",
          pages,
          githubContributorsUrl,
          convertContributorResponse,
          parallelism = 32
        )

        pagedContributors.flatten.toList
      } else List()
    }

    checkGithubApiError("Processing Contributors", response)
    .map(_ => {

      if (200 == response.status) {

        /* current contributors + all other pages in  amount of contributions order */
        val currentContributors = convertContributorResponse(repo, response) match {
          case Success(contributors) => contributors
          case Failure(_) => List()
        }
        val contributors =
          (currentContributors ++ downloadAllPages)
            .sortBy(_.contributions)
            .reverse

        saveJson(githubRepoContributorsPath(paths, repo.repo),
          repo.repo,
          writePretty(contributors))
      }

      ()
    })

  }

  /**
    * Process the downloaded data from repository info
    *
    * @param repo the current repo
    * @param response the response
    * @return
    */
  private def processReadmeResponse(repo: GithubRepo,
                                    response: WSResponse): Try[Unit] = {

    checkGithubApiError("Processing Readme", response)
    .map(_ => {
      if (200 == response.status) {

        val dir = path(paths, repo)
        Files.createDirectories(dir)
        Files.write(
          githubReadmePath(paths, repo),
          GithubReadme
            .absoluteUrl(response.body, repo, "master")
            .getBytes(StandardCharsets.UTF_8)
        )
      }

      ()
    })

  }

  /**
    * Process the downloaded data from repository info
    *
    * @param repo the current repo
    * @param response the response
    * @return
    */
  private def processCommunityProfileResponse(repo: GithubRepo,
                                  response: WSResponse): Try[Unit] = {

    checkGithubApiError("Processing Community Profile", response)
    .map(_ => {
      if (200 == response.status) {

        saveJson(githubRepoCommunityProfilePath(paths, repo), repo, response.body)
      }

      ()
    })

  }

  /**
    * Process the downloaded data from GraphQL API
    * @param repo the current repo
    * @param response the response
    * @return
    */
  private def processTopicsResponse(repo: GithubRepo,
      response: WSResponse): Try[Unit] = {

      checkGithubApiError("Processing Topics", response)
      .map(_ => {
        saveJson(githubRepoTopicsPath(paths, repo), repo, response.body)

        ()
      })

  }

  /**
    * Process the downloaded issues from GraphQL API
    *
    * @param project the current project
    * @param response the response
    * @return
    */
  private def processIssuesResponse(project: Project,
      response: WSResponse): Try[Unit] = {

    checkGithubApiError("Processing Issues", response)
    .map(_ => {
      saveJson(githubRepoIssuesPath(paths, project.githubRepo), project.githubRepo, response.body)

      ()
    })

  }

  private def checkGithubApiError(
      message: String,
      response: WSResponse
      ): Try[Unit] = Try {

    val rateLimitRemaining =
        response.header("X-RateLimit-Remaining").getOrElse("-1").toInt
    if (0 == rateLimitRemaining) {
      val resetAt = new DateTime(
        response.header("X-RateLimit-Reset").getOrElse("0").toLong * 1000)
      throw new Exception(s" $message, hit API Rate Limit by running out of API calls, try again at $resetAt")
    } else if (403 == response.status) {
      val retryAfter = response.header("ResetAt").getOrElse("60").toInt
      throw new Exception(s" $message, hit API Abuse Rate Limit by making too many calls in a small amount of time, try again after $retryAfter s")
    } else if (200 != response.status && 404 != response.status) {
      // 200 is valid response and get 404 for old repo that no longer exists,
      // don't want to throw exception for 404 since it'll stop all other downloads
      throw new Exception(s" $message, Unknown response from Github API, ${response.status}, ${response.body}")
    }

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
    * get the Github community profile url
    *
    * @param repo the current repository
    * @return
    */
  private def githubCommunityProfileUrl(wsClient: AhcWSClient,
                              repo: GithubRepo): WSRequest = {

    applyCommunityProfileHeaders(wsClient.url(mainGithubUrl(repo) + "/community/profile"))
  }

  /**
    * get the Github GraphQL API url
    *
    * @return
    */
  private def githubGraphqlUrl(wsClient: AhcWSClient): WSRequest = {

    applyAcceptJsonHeaders(wsClient.url("https://api.github.com/graphql"))
  }

  /**
    * get the topic query used by the Github GraphQL API
    *
    * @return
    */
  private def topicQuery(repo: GithubRepo): JsObject = {

    val query =
      """
        query($owner:String!, $name:String!) {
          repository(owner: $owner, name: $name) {
            repositoryTopics(first: 50) {
              nodes {
                topic {
                  name
                }
              }
            }
          }
        }
      """
    Json.obj(
      "query" -> query,
      "variables" -> s"""{ "owner": "${repo.organization}", "name": "${repo.repository}" }""")
  }

  /**
    * get the issues query used by the Github GraphQL API
    *
    * @return
    */
  private def issuesQuery(project: Project): JsObject = {

    val query =
      """
        query($owner:String!, $name:String!, $beginnerLabel:String!) {
          repository(owner: $owner, name: $name) {
            issues(last:5, states:OPEN, labels:[$beginnerLabel]) {
              nodes {
                number
                title
                bodyText
                url
              }
            }
          }
        }
      """
    val beginnerIssuesLabel = project.github.flatMap(_.beginnerIssuesLabel).getOrElse("")
    Json.obj(
      "query" -> query,
      "variables" ->
        s"""{ "owner": "${project.organization}",
           |"name": "${project.repository}",
           |"beginnerLabel": "$beginnerIssuesLabel" }""".stripMargin)
  }

  /**
    * process all downloads
    */
  def run(): Unit = {

    downloadGraphql[GithubRepo, Unit]("Downloading Topics",
                               githubRepos,
                               githubGraphqlUrl,
                               topicQuery,
                               processTopicsResponse,
                               parallelism = 32)

    downloadGithub[GithubRepo, Unit]("Downloading Repo Info",
                               githubRepos,
                               githubInfoUrl,
                               processInfoResponse,
                               parallelism = 32)

    downloadGithub[GithubRepo, Unit]("Downloading Community Profile info",
                                githubRepos,
                                githubCommunityProfileUrl,
                                processCommunityProfileResponse,
                                parallelism = 32)

    downloadGithub[GithubRepo, Unit]("Downloading Readme",
                               githubRepos,
                               githubReadmeUrl,
                               processReadmeResponse,
                               parallelism = 32)

    // todo: for later @see #112 - remember that issues are paginated - see contributors */
    // download[GithubRepo, Unit]("Downloading Issues", githubRepos, githubIssuesUrl, processIssuesResponse)

    downloadGithub[PaginatedGithub, Unit]("Downloading Contributors",
                                    paginatedGithubRepos,
                                    githubContributorsUrl,
                                    processContributorResponse,
                                    parallelism = 32)

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

      downloadGraphql[GithubRepo, Unit]("Downloading Topics",
                                 Set(repo),
                                 githubGraphqlUrl,
                                 topicQuery,
                                 processTopicsResponse,
                                 parallelism = 32)

      downloadGithub[GithubRepo, Unit]("Downloading Repo Info",
                                 Set(repo),
                                 githubInfoUrl,
                                 processInfoResponse,
                                 parallelism = 32)

      downloadGithub[GithubRepo, Unit]("Downloading Community Profile info",
                                Set(repo),
                                githubCommunityProfileUrl,
                                processCommunityProfileResponse,
                                parallelism = 32)
    }

    if (readme) {

      downloadGithub[GithubRepo, Unit]("Downloading Readme",
                                 Set(repo),
                                 githubReadmeUrl,
                                 processReadmeResponse,
                                 parallelism = 32)
    }

    if (contributors) {

      val paginated = Set(PaginatedGithub(repo, 1))
      downloadGithub[PaginatedGithub, Unit]("Downloading Contributors",
                                      paginated,
                                      githubContributorsUrl,
                                      processContributorResponse,
                                      parallelism = 32)
    }

    ()
  }

}
