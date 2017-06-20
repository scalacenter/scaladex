package ch.epfl.scala.index
package data
package github

import download.PlayWsDownloader
import cleanup.GithubRepoExtractor
import maven.PomsReader
import model.misc.GithubRepo

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

class GithubDownload(paths: DataPaths,
                     privateCredentials: Option[GithubCredentials] = None)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer)
    extends PlayWsDownloader {

  import Json4s._

  case class PaginatedGithub(repo: GithubRepo, page: Int)

  private lazy val githubRepoExtractor = new GithubRepoExtractor(paths)
  private lazy val githubRepos = {
    PomsReader
      .loadAll(paths)
      .collect { case Success((pom, _, _)) => githubRepoExtractor(pom) }
      .flatten
      .toSet
  }
  private lazy val paginatedGithubRepos =
    githubRepos.map(repo => PaginatedGithub(repo, 1))

  private val config =
    ConfigFactory.load().getConfig("org.scala_lang.index.data")
  private val tokens = config.getStringList("github").toArray

  private def credentials = {
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
        request.addHttpHeaders("Authorization" -> s"token ${cred.token}"))
      .getOrElse(
        request.addHttpHeaders("Authorization" -> s"token $credentials"))
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
        "Accept" -> "application/vnd.github.VERSION.html"))
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
                                  response: WSResponse): Unit = {

    if (200 == response.status) {

      saveJson(githubRepoInfoPath(paths, repo), repo, response.body)
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
  // private def processIssuesResponse(repo: GithubRepo,
  //                                   response: WSResponse): Unit = {

  //   if (200 == response.status) {

  //     saveJson(githubRepoIssuesPath(paths, repo), repo, response.body)
  //   }

  //   ()
  // }

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
          convertContributorResponse,
          parallelism = 32
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

      saveJson(githubRepoContributorsPath(paths, repo.repo),
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
  }

  /**
    * Process the downloaded data from GraphQL API
    *
    * @param repo the current repo
    * @param response the response
    * @return
    */
  private def processTopicsResponse(isRetry: Boolean)(
      repo: GithubRepo,
      response: WSResponse,
      client: AhcWSClient): Unit = {

    if (200 == response.status) {

      val rateLimitRemaining =
        response.header("X-RateLimit-Remaining").getOrElse("-1").toInt
      if (0 != rateLimitRemaining) {
        saveJson(githubRepoTopicsPath(paths, repo), repo, response.body)
      } else {
        // ran out of API calls to GraphQL API, try again after the API calls reset
        // to view current rate limit remaining for an apiToken:
        // curl -H "Authorization: bearer apiToken" -X POST -d '{"query": "query { rateLimit { limit cost remaining resetAt } }"}' https://api.github.com/graphql
        val resetAt = new DateTime(
          response.header("X-RateLimit-Reset").getOrElse("0").toLong * 1000)
        throw new Exception(
          s"ERROR downloading Topics, stopping - RateLimitRemaining = 0, try again at $resetAt")
      }
    } else if (403 == response.status) {

      // abuse rate limit error thrown by github, only try to download again one more time
      if (!isRetry) {
        val retryAfter = response.header("Retry-After").getOrElse("60").toInt
        println(
          s"Thread ${Thread.currentThread().getId}, ${repo}, Stopping for ${retryAfter} s, ${response.status} response")
        Thread.sleep((retryAfter * 1000).toLong)
        println(s"Thread ${Thread.currentThread().getId}, ${repo}, Continuing")

        retryDownload[GithubRepo, Unit](repo,
                                        githubGraphqlUrl,
                                        topicQuery,
                                        processTopicsResponse(true),
                                        client)
      }
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
  // private def githubIssuesUrl(wsClient: AhcWSClient,
  //                             repo: GithubRepo): WSRequest = {
  //   applyAcceptJsonHeaders(wsClient.url(mainGithubUrl(repo) + "/issues"))
  // }

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
    * get the Github GraphQL API url
    *
    * @return
    */
  private def githubGraphqlUrl(wsClient: AhcWSClient,
                               repo: GithubRepo): WSRequest = {

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
    * process all downloads
    */
  def run(): Unit = {

    download[GithubRepo, Unit]("Downloading Repo Info",
                               githubRepos,
                               githubInfoUrl,
                               processInfoResponse,
                               parallelism = 32)
    download[GithubRepo, Unit]("Downloading Topics",
                               githubRepos,
                               githubGraphqlUrl,
                               null,
                               parallelism = 32,
                               graphqlQuery = topicQuery,
                               graphqlProcess = processTopicsResponse(false))
    download[GithubRepo, Unit]("Downloading Readme",
                               githubRepos,
                               githubReadmeUrl,
                               processReadmeResponse,
                               parallelism = 32)
    // todo: for later @see #112 - remember that issues are paginated - see contributors */
    // download[GithubRepo, Unit]("Downloading Issues", githubRepos, githubIssuesUrl, processIssuesResponse)
    download[PaginatedGithub, Unit]("Downloading Contributors",
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

      download[GithubRepo, Unit]("Downloading Repo Info",
                                 Set(repo),
                                 githubInfoUrl,
                                 processInfoResponse,
                                 parallelism = 32)

      download[GithubRepo, Unit]("Downloading Topics",
                                 Set(repo),
                                 githubGraphqlUrl,
                                 null,
                                 parallelism = 32,
                                 graphqlQuery = topicQuery,
                                 graphqlProcess = processTopicsResponse(false))
    }

    if (readme) {

      download[GithubRepo, Unit]("Downloading Readme",
                                 Set(repo),
                                 githubReadmeUrl,
                                 processReadmeResponse,
                                 parallelism = 32)
    }

    if (contributors) {

      val paginated = Set(PaginatedGithub(repo, 1))
      download[PaginatedGithub, Unit]("Downloading Contributors",
                                      paginated,
                                      githubContributorsUrl,
                                      processContributorResponse,
                                      parallelism = 32)
    }

    ()
  }

}
