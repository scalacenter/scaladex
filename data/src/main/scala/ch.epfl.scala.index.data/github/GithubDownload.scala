package ch.epfl.scala.index
package data
package github

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import ch.epfl.scala.index.data.cleanup.GithubRepoExtractor
import ch.epfl.scala.index.data.download.{PlayWsClient, PlayWsDownloader}
import ch.epfl.scala.index.data.elastic.SaveLiveData
import ch.epfl.scala.index.data.maven.PomsReader
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.misc.GithubRepo
import com.typesafe.config.ConfigFactory
import jawn.support.json4s.Parser
import org.joda.time.DateTime
import org.json4s.native.Serialization._
import org.slf4j.LoggerFactory
import play.api.libs.json._
import play.api.libs.ws._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util._

class GithubDownload(paths: DataPaths,
                     privateCredentials: Option[Credentials] = None)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer
) extends PlayWsDownloader {

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

  private lazy val credential =
    if (config.hasPath("github")) {
      val creds = config.getStringList("github").toArray
      if (0 == creds.length) {
        sys.error("Need at least 1 GitHub token, see CONTRIBUTING.md#GitHub")
      } else if (2 < creds.length) {
        sys.error(
          "Can only use maximum of 2 GitHub tokens, see CONTRIBUTING.md#GitHub"
        )
      } else {
        creds
      }
    } else {
      sys.error("Setup your GitHub token see CONTRIBUTING.md#GitHub")
    }

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
        .getOrElse(credential(scala.util.Random.nextInt(credential.length)))

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
        "Accept" -> "application/vnd.github.black-panther-preview+json"
      )
    )
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
      writePretty(Parser.parseUnsafe(data)).getBytes(StandardCharsets.UTF_8)
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

    checkGithubApiError(s"Processing Info for $repo", response)
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
      response: WSResponse
  ): Try[List[V3.Contributor]] = {

    checkGithubApiError(s"Converting Contributors for ${repo.repo}", response)
      .map(_ => {
        if (200 == response.status) {

          read[List[V3.Contributor]](response.body)
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
    def downloadAllPages: List[V3.Contributor] = {

      val lastPage = response.header("Link").map(extractLastPage).getOrElse(1)

      if (1 == repo.page && 1 < lastPage) {

        val pages = List
          .range(2, lastPage + 1)
          .map(page => PaginatedGithub(repo.repo, page))
          .toSet

        val pagedContributors =
          downloadGithub[PaginatedGithub, List[V3.Contributor]](
            "Download contributor Pages",
            pages,
            githubContributorsUrl,
            convertContributorResponse
          )

        pagedContributors.flatten.toList
      } else List()
    }

    checkGithubApiError(s"Processing Contributors for ${repo.repo}", response)
      .map(_ => {

        if (200 == response.status) {

          /* current contributors + all other pages in  amount of contributions order */
          val currentContributors =
            convertContributorResponse(repo, response) match {
              case Success(contributors) => contributors
              case Failure(_)            => List()
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

    checkGithubApiError(s"Processing Readme for $repo", response)
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
  private def processCommunityProfileResponse(
      repo: GithubRepo,
      response: WSResponse
  ): Try[Unit] = {

    checkGithubApiError(s"Processing Community Profile for $repo", response)
      .map(_ => {
        if (200 == response.status) {

          saveJson(githubRepoCommunityProfilePath(paths, repo),
                   repo,
                   response.body)
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

    checkGithubApiError(s"Processing Topics for $repo", response)
      .map(_ => {
        saveJson(githubRepoTopicsPath(paths, repo), repo, response.body)

        ()
      })

  }

  /**
   * Process the downloaded issues from GraphQL API
   *
   * @param in the current repo
   * @param response the response
   * @return
   */
  private def processIssuesResponse(in: (GithubRepo, String),
                                    response: WSResponse): Try[Unit] = {

    val (repo, _) = in

    checkGithubApiError(s"Processing Issues for ${repo}", response)
      .map(_ => {
        saveJson(githubRepoIssuesPath(paths, repo), repo, response.body)

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
      pauseRateLimitReset()
    } else if (403 == response.status) {
      val retryAfter = response.header("ResetAt").getOrElse("60").toInt
      throw new Exception(
        s" $message, hit Github API Abuse Rate Limit by making too many calls in a small amount of time, try again after $retryAfter s"
      )
    } else if (500 == response.status) {
      log.info(s" $message, received 500 response from github")
    } else if (200 != response.status &&
               404 != response.status &&
               204 != response.status) {
      // get 200 for valid response
      // get 404 for old repo that no longer exists
      // get 204 when getting contributors for empty repo,
      //   https:/api.github.com/repos/rockjam/cbt-sonatype/contributors?page=1
      throw new Exception(
        s" $message, Unknown response from Github API, ${response.status}, ${response.body}"
      )
    }
  }

  private def pauseRateLimitReset() = {

    // note: only have to compare REST API for both tokens since only hitting GraphQL API for topics
    // so won't hit rate limit but if you hit GraphQL API for more things, you would
    // need to check rate limit reset times for GraphQL API as well and pick max one

    val (reset1, reset2) = PlayWsClient.open().acquireAndGet { client =>
      val baseRequest = client
        .url("https://api.github.com")
        .addHttpHeaders("Accept" -> "application/json")

      val request1 =
        baseRequest
          .addHttpHeaders("Authorization" -> s"bearer ${credential(0)}")
      val response1 = Await.result(request1.get, Duration.Inf)
      val reset1 = new DateTime(
        response1.header("X-RateLimit-Reset").getOrElse("0").toLong * 1000
      )

      val reset2 = if (credential.length == 2) {
        val request2 = baseRequest.addHttpHeaders(
          "Authorization" -> s"bearer ${credential(1)}"
        )
        val response2 = Await.result(request2.get, Duration.Inf)
        new DateTime(
          response2.header("X-RateLimit-Reset").getOrElse("0").toLong * 1000
        )
      } else {
        reset1
      }

      (reset1, reset2)
    }

    val maxReset = if (reset1.compareTo(reset2) > 0) reset1 else reset2
    val milliseconds = maxReset.getMillis - DateTime.now().getMillis
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    log.info(
      s"PAUSING, Hit Github API rate limit so pausing for $minutes mins to wait for rate limits for tokens to reset at $maxReset"
    )
    Thread.sleep(milliseconds)
    log.info(s"RESUMING")
  }

  private def processChatroomResponse(repo: GithubRepo,
                                      response: WSResponse): Unit = {

    if (200 == response.status) {

      val dir = path(paths, repo)
      Files.createDirectories(dir)

      Files.write(
        githubRepoChatroomPath(paths, repo),
        gitterUrlString(repo).getBytes(StandardCharsets.UTF_8)
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
  private def githubInfoUrl(wsClient: WSClient, repo: GithubRepo): WSRequest = {

    applyAcceptJsonHeaders(wsClient.url(mainGithubUrl(repo)))
  }

  /**
   * get the Github readme url
   *
   * @param repo the current repository
   * @return
   */
  private def githubReadmeUrl(wsClient: WSClient, repo: GithubRepo): WSRequest = {

    applyReadmeHeaders(wsClient.url(mainGithubUrl(repo) + "/readme"))
  }

  /**
   * get the Github contributors url
   *
   * @param repo the current repository
   * @return
   */
  private def githubContributorsUrl(wsClient: WSClient,
                                    repo: PaginatedGithub): WSRequest = {

    applyAcceptJsonHeaders(
      wsClient
        .url(mainGithubUrl(repo.repo) + s"/contributors?page=${repo.page}")
    )
  }

  /**
   * get the Github community profile url
   *
   * @param repo the current repository
   * @return
   */
  private def githubCommunityProfileUrl(wsClient: WSClient,
                                        repo: GithubRepo): WSRequest = {

    applyCommunityProfileHeaders(
      wsClient.url(mainGithubUrl(repo) + "/community/profile")
    )
  }

  /**
   * get the Github GraphQL API url
   *
   * @return
   */
  private def githubGraphqlUrl(wsClient: WSClient): WSRequest = {

    applyAcceptJsonHeaders(wsClient.url("https://api.github.com/graphql"))
  }

  /**
   * request for potential url for project's gitter room
   *
   * @param repo the current github repo
   * @return
   */
  private def gitterUrl(wsClient: WSClient, repo: GithubRepo): WSRequest = {
    wsClient.url(gitterUrlString(repo))
  }

  /**
   * potential url for project's gitter room
   *
   * @param repo the current github repo
   * @return
   */
  private def gitterUrlString(repo: GithubRepo): String = {
    s"https://gitter.im/${repo.organization}/${repo.repository}"
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
      "variables" -> s"""{ "owner": "${repo.organization}", "name": "${repo.repository}" }"""
    )
  }

  /**
   * get the issues query used by the Github GraphQL API
   *
   * @return
   */
  private def issuesQuery(in: (GithubRepo, String)): JsObject = {
    val (repo, beginnerIssuesLabel) = in

    val query =
      """
        query($owner:String!, $name:String!, $beginnerLabel:String!) {
          repository(owner: $owner, name: $name) {
            issues(last:100, states:OPEN, labels:[$beginnerLabel]) {
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
    Json.obj(
      "query" -> query,
      "variables" ->
        s"""{ "owner": "${repo.organization}",
           |"name": "${repo.repository}",
           |"beginnerLabel": "$beginnerIssuesLabel" }""".stripMargin
    )
  }

  /**
   * process all downloads
   */
  def run(): Unit = {

    downloadGithub[GithubRepo, Unit]("Downloading Repo Info",
                                     githubRepos,
                                     githubInfoUrl,
                                     processInfoResponse)

    downloadGraphql[GithubRepo, Unit]("Downloading Topics",
                                      githubRepos,
                                      githubGraphqlUrl,
                                      topicQuery,
                                      processTopicsResponse)

    downloadGithub[GithubRepo, Unit]("Downloading Community Profile info",
                                     githubRepos,
                                     githubCommunityProfileUrl,
                                     processCommunityProfileResponse)

    downloadGithub[GithubRepo, Unit]("Downloading Readme",
                                     githubRepos,
                                     githubReadmeUrl,
                                     processReadmeResponse)

    // todo: for later @see #112 - remember that issues are paginated - see contributors */
    // download[GithubRepo, Unit]("Downloading Issues", githubRepos, githubIssuesUrl, processIssuesResponse)

    downloadGithub[PaginatedGithub, Unit]("Downloading Contributors",
                                          paginatedGithubRepos,
                                          githubContributorsUrl,
                                          processContributorResponse)

    download[GithubRepo, Unit]("Checking Gitter Chatroom Links",
                               githubRepos,
                               gitterUrl,
                               processChatroomResponse,
                               parallelism = 32)

    val reposAndBeginnerIssues: Set[(GithubRepo, String)] = {
      val liveProjecs = SaveLiveData.storedProjects(paths)

      val projectReferences =
        githubRepos.map {
          case repo @ GithubRepo(organization, repository) =>
            (repo, Project.Reference(organization, repository))
        }.toList

      projectReferences
        .map {
          case (repo, reference) =>
            liveProjecs
              .get(reference)
              .flatMap(
                form => form.beginnerIssuesLabel.map(label => (repo, label))
              )
        }
        .flatten
        .toSet
    }

    downloadGraphql[(GithubRepo, String), Unit](
      "Downloading Beginner Issues",
      reposAndBeginnerIssues,
      githubGraphqlUrl,
      issuesQuery,
      processIssuesResponse
    )

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

      downloadGithub[GithubRepo, Unit]("Downloading Repo Info",
                                       Set(repo),
                                       githubInfoUrl,
                                       processInfoResponse)

      downloadGraphql[GithubRepo, Unit]("Downloading Topics",
                                        Set(repo),
                                        githubGraphqlUrl,
                                        topicQuery,
                                        processTopicsResponse)

      downloadGithub[GithubRepo, Unit]("Downloading Community Profile info",
                                       Set(repo),
                                       githubCommunityProfileUrl,
                                       processCommunityProfileResponse)
    }

    if (readme) {

      downloadGithub[GithubRepo, Unit]("Downloading Readme",
                                       Set(repo),
                                       githubReadmeUrl,
                                       processReadmeResponse)
    }

    if (contributors) {

      val paginated = Set(PaginatedGithub(repo, 1))
      downloadGithub[PaginatedGithub, Unit]("Downloading Contributors",
                                            paginated,
                                            githubContributorsUrl,
                                            processContributorResponse)
    }

    download[GithubRepo, Unit]("Checking Gitter Chatroom Links",
                               Set(repo),
                               gitterUrl,
                               processChatroomResponse,
                               parallelism = 32)

    ()
  }

  def runBeginnerIssues(repo: GithubRepo, beginnerIssuesLabel: String): Unit = {

    downloadGraphql[(GithubRepo, String), Unit](
      "Downloading Beginner Issues",
      Set((repo, beginnerIssuesLabel)),
      githubGraphqlUrl,
      issuesQuery,
      processIssuesResponse
    )
  }

}
