package scaladex.infra

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ResponseEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.OverflowStrategy
import akka.stream.QueueOfferResult
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.syntax._
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.Project
import scaladex.core.model.Url
import scaladex.core.model.UserInfo
import scaladex.core.service.GithubService
import scaladex.core.util.ScalaExtensions._
import scaladex.core.util.Secret
import scaladex.infra.github.GithubModel

class GithubClient(token: Secret)(implicit system: ActorSystem) extends GithubService with LazyLogging {
  private val credentials: OAuth2BearerToken = OAuth2BearerToken(token.decode)
  private val acceptJson = RawHeader("Accept", "application/vnd.github.v3+json")
  private val acceptHtmlVersion = RawHeader("Accept", "application/vnd.github.VERSION.html")

  private implicit val ec: ExecutionContextExecutor = system.dispatcher
  private val poolClientFlow =
    Http()
      .cachedHostConnectionPoolHttps[Promise[HttpResponse]](
        "api.github.com",
        // in recursive functions, we have timeouts, and I didn't know how to fix the issue so I increased the timeout
        // Maybe put this configuration in a configuration file
        settings = ConnectionPoolSettings(
          "akka.http.host-connection-pool.response-entity-subscription-timeout = 10.seconds"
        ).copy(maxConnections = 10)
      )
      .throttle(
        elements = 5000,
        per = 1.hour
      )

  val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] =
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](100, OverflowStrategy.dropNew)
      .via(poolClientFlow)
      .toMat(Sink.foreach {
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p)    => p.failure(e)
      })(Keep.left)
      .run()

  override def getProjectInfo(ref: Project.Reference): Future[GithubResponse[(Project.Reference, GithubInfo)]] =
    getRepository(ref).flatMap {
      case GithubResponse.Failed(code, reason) => Future.successful(GithubResponse.Failed(code, reason))
      case GithubResponse.Ok(repo) =>
        getRepoInfo(repo).map(info => GithubResponse.Ok(repo.ref -> info))
      case GithubResponse.MovedPermanently(repo) =>
        getRepoInfo(repo).map(info => GithubResponse.MovedPermanently(repo.ref -> info))
    }

  private def getRepoInfo(repo: GithubModel.Repository): Future[GithubInfo] =
    for {
      readme <- getReadme(repo.ref)
      communityProfile <- getCommunityProfile(repo.ref)
      contributors <- getContributors(repo.ref)
      openIssues <- getOpenIssues(repo.ref)
      chatroom <- getGitterChatRoom(repo.ref)
    } yield GithubInfo(
      homepage = repo.homepage.map(Url),
      description = repo.description,
      logo = Option(repo.avatartUrl).map(Url),
      stars = Option(repo.stargazers_count),
      forks = Option(repo.forks),
      watchers = Option(repo.subscribers_count),
      issues = Option(repo.open_issues),
      creationDate = repo.creationDate,
      readme = readme,
      contributors = contributors.map(_.toGithubContributor),
      commits = Some(contributors.foldLeft(0)(_ + _.contributions)),
      topics = repo.topics.toSet,
      contributingGuide = communityProfile.flatMap(_.contributingFile).map(Url),
      codeOfConduct = communityProfile.flatMap(_.codeOfConductFile).map(Url),
      chatroom = chatroom.map(Url),
      openIssues = openIssues.map(_.toGithubIssue).toList
    )

  def getReadme(ref: Project.Reference): Future[Option[String]] = {
    val request = HttpRequest(uri = s"${repoUrl(ref)}/readme")
      .addCredentials(credentials)
      .addHeader(acceptHtmlVersion)

    getRaw(request)
      .flatMap {
        case (_, entity) =>
          entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String).map(Option.apply)
      }
      .fallbackTo(Future.successful(None))
  }

  def getCommunityProfile(ref: Project.Reference): Future[Option[GithubModel.CommunityProfile]] = {
    val request = HttpRequest(uri = s"${repoUrl(ref)}/community/profile")
      .addCredentials(credentials)
      .addHeader(RawHeader("Accept", "application/vnd.github.black-panther-preview+json"))
    get[GithubModel.CommunityProfile](request)
      .map(Some.apply)
      .fallbackTo(Future.successful(None))
  }

  def getContributors(ref: Project.Reference): Future[List[GithubModel.Contributor]] = {
    def request(page: Int) =
      HttpRequest(uri = s"${repoUrl(ref)}/contributors?${perPage()}&page=$page")
        .addHeader(acceptJson)
        .addCredentials(credentials)

    def getContributionPage(page: Int): Future[List[GithubModel.Contributor]] =
      get[List[GithubModel.Contributor]](request(page))

    getRaw(request(page = 1))
      .flatMap {
        case (headers, entity) =>
          val lastPage = headers.find(_.is("link")).map(_.value()).flatMap(extractLastPage)
          lastPage match {
            case Some(lastPage) if lastPage > 1 =>
              for {
                page1 <- Unmarshal(entity).to[List[GithubModel.Contributor]]
                nextPages <- (2 to lastPage).mapSync(getContributionPage).map(_.flatten)
              } yield page1 ++ nextPages

            case _ => Unmarshal(entity).to[List[GithubModel.Contributor]]
          }
      }
      .fallbackTo(Future.successful(List.empty))
  }

  def getRepository(ref: Project.Reference): Future[GithubResponse[GithubModel.Repository]] = {
    val request = HttpRequest(uri = s"${repoUrl(ref)}").addHeader(acceptJson).addCredentials(credentials)
    process(request).flatMap {
      case GithubResponse.Ok((_, entity)) =>
        Unmarshal(entity).to[GithubModel.Repository].map(GithubResponse.Ok(_))
      case GithubResponse.MovedPermanently((_, entity)) =>
        Unmarshal(entity).to[GithubModel.Repository].map(GithubResponse.MovedPermanently(_))
      case GithubResponse.Failed(code, reason) => Future.successful(GithubResponse.Failed(code, reason))
    }
  }

  def getOpenIssues(ref: Project.Reference): Future[Seq[GithubModel.OpenIssue]] = {
    def request(page: Int) =
      HttpRequest(uri = s"${repoUrl(ref)}/issues?${perPage()}&page=$page")
        .addHeader(acceptJson)
        .addCredentials(credentials)

    def getOpenIssuePage(page: Int): Future[Seq[GithubModel.OpenIssue]] =
      get[Seq[Option[GithubModel.OpenIssue]]](request(page)).map(_.flatten)

    getRaw(request(page = 1))
      .flatMap {
        case (headers, entity) =>
          val lastPage = headers.find(_.is("link")).map(_.value()).flatMap(extractLastPage)
          lastPage match {
            case Some(lastPage) if lastPage > 1 =>
              for {
                page1 <- Unmarshal(entity).to[Seq[Option[GithubModel.OpenIssue]]]
                nextPages <- (2 to lastPage).mapSync(getOpenIssuePage).map(_.flatten)
              } yield page1.flatten ++ nextPages

            case _ => Unmarshal(entity).to[Seq[Option[GithubModel.OpenIssue]]].map(_.flatten)
          }
      }
      .fallbackTo(Future.successful(Seq.empty))
  }

  def getGitterChatRoom(ref: Project.Reference): Future[Option[String]] = {
    val uri = s"https://gitter.im/$ref"
    val request = HttpRequest(uri = uri)
    Http().singleRequest(request).map {
      case _ @HttpResponse(StatusCodes.OK, _, entity, _) =>
        entity.discardBytes()
        Some(uri)
      case resp =>
        resp.entity.discardBytes()
        None
    }
  }

  def getUserOrganizationRepositories(
      login: String,
      filterPermissions: Seq[String]
  ): Future[Seq[Project.Reference]] = {
    val query =
      s"""|query {
          |  user(login: "$login") {
          |    organizations(first: 100) {
          |      pageInfo {
          |        endCursor
          |        hasNextPage
          |      }
          |      nodes {
          |        repositories(first: 100, affiliations: [COLLABORATOR, ORGANIZATION_MEMBER, OWNER]) {
          |          pageInfo {
          |            endCursor
          |            hasNextPage
          |          }
          |          nodes {
          |            nameWithOwner
          |            viewerPermission
          |          }
          |        }
          |      }
          |    }
          |  }
          |}""".stripMargin

    val request = graphqlRequest(query)
    val githubRepoPage1 = get[List[GithubModel.RepoWithPermissionPage]](request)
    if (filterPermissions.isEmpty) githubRepoPage1.map(_.flatMap(_.toGithubRepos))
    else
      githubRepoPage1.map(_.flatMap(_.toGithubReposWithPermission).collect {
        case (repo, permission) if filterPermissions.contains(permission) => repo
      })
  }
  def getUserRepositories(login: String, filterPermissions: Seq[String]): Future[Seq[Project.Reference]] = {
    def query(endcursorOpt: Option[String]) = {
      val after = endcursorOpt.map(endcursor => s"""after: "$endcursor"""").getOrElse("")
      s"""|query {
          |  user(login: "$login") {
          |    repositories(first: 100, $after) {
          |
          |      pageInfo {
          |        endCursor
          |        hasNextPage
          |      }
          |      nodes {
          |        nameWithOwner
          |        viewerPermission
          |      }
          |    }
          |  }
          |}
          |
          |""".stripMargin
    }

    val request = graphqlRequest(query(None))
    val page1 = get[GithubModel.RepoWithPermissionPage](request)(GithubModel.decoderForUserRepo)
    def getPageRecursively(
        pageFut: Future[GithubModel.RepoWithPermissionPage]
    )(previous: Future[Seq[GithubModel.RepoWithPermissionPage]]): Future[Seq[GithubModel.RepoWithPermissionPage]] =
      pageFut.flatMap { page =>
        if (!page.hasNextPage) previous.map(page +: _)
        else {
          val nextPage = get[GithubModel.RepoWithPermissionPage](graphqlRequest(query(Some(page.endCursor))))(
            GithubModel.decoderForUserRepo
          )
          getPageRecursively(nextPage)(previous.map(page +: _))
        }
      }
    val pages = getPageRecursively(page1)(Future.successful(Nil))

    if (filterPermissions.isEmpty) pages.map(_.flatMap(_.toGithubRepos))
    else
      pages.map(_.flatMap(_.toGithubReposWithPermission).collect {
        case (repo, permission) if filterPermissions.contains(permission) => repo
      })
  }

  def getUserInfo(): Future[UserInfo] = {
    val query =
      """|query {
         |  viewer {
         |    login
         |    avatarUrl
         |    name
         |  }
         |}""".stripMargin
    val request = graphqlRequest(query)
    val userInfo = get[GithubModel.UserInfo](request)

    userInfo.map(_.toCoreUserInfo(token))
  }

  // only the first 100 orgs
  def getUserOrganizations(login: String): Future[Set[Project.Organization]] = {
    val query =
      s"""|query {
          |  user(login: "$login") {
          |    organizations(first: 100) {
          |      nodes {
          |        login
          |      }
          |    }
          |  }
          |}""".stripMargin
    val request = graphqlRequest(query)
    val organizations = get[Seq[GithubModel.Organization]](request)
    organizations.map(_.map(_.toCoreOrganization).toSet)
  }

  private def extractLastPage(links: String): Option[Int] = {
    val pattern = """page=([0-9]+)>; rel="?last"?""".r
    pattern
      .findFirstMatchIn(links)
      .flatMap(mtch => Try(mtch.group(1).toInt).toOption)
  }

  private def queueRequest(request: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    queue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued    => responsePromise.future
      case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(
          new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later.")
        )
    }
  }

  private def graphqlRequest(query: String): HttpRequest = {
    val json = Map("query" -> query.asJson).asJson
    HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("https://api.github.com/graphql"),
      entity = HttpEntity(ContentTypes.`application/json`, json.toString()),
      headers = List(Authorization(credentials))
    )
  }

  private def get[A](
      request: HttpRequest
  )(implicit decoder: io.circe.Decoder[A]): Future[A] =
    getRaw(request).flatMap { case (_, entity) => Unmarshal(entity).to[A] }

  private def getRaw(request: HttpRequest): Future[(Seq[HttpHeader], ResponseEntity)] =
    process(request).map {
      case GithubResponse.Ok((headers, entity))               => (headers, entity)
      case GithubResponse.MovedPermanently((headers, entity)) => (headers, entity)
      case GithubResponse.Failed(code, reason)                => throw new Exception(s"$code: $reason")
    }

  private def process(request: HttpRequest): Future[GithubResponse[(Seq[HttpHeader], ResponseEntity)]] = {
    assert(request.headers.contains(Authorization(credentials)))
    queueRequest(request).flatMap {
      case r @ HttpResponse(StatusCodes.OK, headers, entity, _) =>
        Future.successful(GithubResponse.Ok((headers, entity)))
      case r @ HttpResponse(StatusCodes.MovedPermanently, headers, entity, _) =>
        entity.discardBytes()
        val newRequest = HttpRequest(uri = headers.find(_.is("location")).get.value()).withHeaders(request.headers)
        process(newRequest).map {
          case GithubResponse.Ok(res) => GithubResponse.MovedPermanently(res)
          case other                  => other
        }
      case _ @HttpResponse(code, _, entity, _) =>
        implicit val unmarshaller = Unmarshaller.byteStringUnmarshaller
        Unmarshal(entity).to[String].map(errorMessage => GithubResponse.Failed(code.intValue, errorMessage))
    }
  }

  private def repoUrl(ref: Project.Reference): String =
    s"https://api.github.com/repos/$ref"

  private def perPage(value: Int = 100) = s"per_page=$value"
}

object GithubClient {}
