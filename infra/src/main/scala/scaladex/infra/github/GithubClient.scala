package scaladex.infra.github

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
import scaladex.infra.github.GithubModel.Contributor

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

  override def update(ref: Project.Reference): Future[GithubResponse[GithubInfo]] =
    getRepoInfo(ref).flatMap {
      case GithubResponse.Failed(code, reason) => Future.successful(GithubResponse.Failed(code, reason))
      case GithubResponse.Ok(res) =>
        update(res).map(GithubResponse.Ok.apply)
      case GithubResponse.MovedPermanently(res) =>
        update(res).map(GithubResponse.MovedPermanently.apply)
    }

  private def update(repoInfo: GithubModel.Repository): Future[GithubInfo] = {
    val ref = repoInfo.projectRef
    for {
      readme <- getReadme(ref)
      communityProfile <- getCommunityProfile(ref)
      contributors <- getContributors(ref)
      openIssues <- getOpenIssues(ref)
      chatroom <- getGiterChatRoom(ref)
    } yield GithubInfo(
      projectRef = ref,
      homepage = repoInfo.homepage.map(Url),
      description = repoInfo.description,
      logo = Option(repoInfo.avatartUrl).map(Url),
      stars = Option(repoInfo.stargazers_count),
      forks = Option(repoInfo.forks),
      watchers = Option(repoInfo.subscribers_count),
      issues = Option(repoInfo.open_issues),
      creationDate = repoInfo.creationDate,
      readme = readme,
      contributors = contributors.map(_.toGithubContributor),
      commits = Some(contributors.foldLeft(0)(_ + _.contributions)),
      topics = repoInfo.topics.toSet,
      contributingGuide = communityProfile.flatMap(_.contributingFile).map(Url),
      codeOfConduct = communityProfile.flatMap(_.codeOfConductFile).map(Url),
      chatroom = chatroom.map(Url),
      beginnerIssues = openIssues.map(_.toGithubIssue).toList
    )
  }

  def getReadme(ref: Project.Reference): Future[Option[String]] = {
    val request = HttpRequest(uri = s"${mainGithubUrl(ref)}/readme")
      .addCredentials(credentials)
      .addHeader(acceptHtmlVersion)

    getRaw(request).failWithTry.flatMap {
      case Success((_, entity)) =>
        entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String).map(Option.apply)
      case Failure(_) =>
        // a project may not have a readme file
        Future.successful(None)
    }
  }

  def getCommunityProfile(ref: Project.Reference): Future[Option[GithubModel.CommunityProfile]] = {
    val request = HttpRequest(uri = s"${mainGithubUrl(ref)}/community/profile")
      .addCredentials(credentials)
      .addHeader(RawHeader("Accept", "application/vnd.github.black-panther-preview+json"))
    get[GithubModel.CommunityProfile](request).failWithTry
      .map {
        case Success(profile)   => Some(profile)
        case Failure(exception) => None
      }
  }

  def getContributors(ref: Project.Reference): Future[List[Contributor]] = {
    def url(page: Int = 1) = HttpRequest(uri = s"${mainGithubUrl(ref)}/contributors?${perPage()}&${inPage(page)}")

    def getContributionPage(page: Int): Future[List[Contributor]] = {
      val request = url(page).addHeader(acceptJson).addCredentials(credentials)
      get[List[Contributor]](request)
    }

    val firstPage = url().addHeader(acceptJson).addCredentials(credentials)
    getRaw(firstPage).flatMap {
      case (headers, entity) =>
        val lastPage = headers.find(_.is("link")).map(_.value()).flatMap(extractLastPage)
        lastPage match {
          case Some(lastPage) if lastPage > 1 =>
            for {
              page1 <- Unmarshal(entity).to[List[Contributor]]
              nextPages <- (2 to lastPage).mapSync(getContributionPage).map(_.flatten)
            } yield page1 ++ nextPages.toList

          case _ => Unmarshal(entity).to[List[Contributor]]
        }
    }
  }

  def getRepoInfo(ref: Project.Reference): Future[GithubResponse[GithubModel.Repository]] = {
    val request = HttpRequest(uri = s"${mainGithubUrl(ref)}").addHeader(acceptJson).addCredentials(credentials)
    process(request).flatMap {
      case GithubResponse.Ok((_, entity)) =>
        Unmarshal(entity).to[GithubModel.Repository].map(GithubResponse.Ok(_))
      case GithubResponse.MovedPermanently((_, entity)) =>
        Unmarshal(entity).to[GithubModel.Repository].map(GithubResponse.MovedPermanently(_))
      case GithubResponse.Failed(code, reason) => Future.successful(GithubResponse.Failed(code, reason))
    }
  }

  def getOpenIssues(ref: Project.Reference): Future[Seq[GithubModel.OpenIssue]] = {
    def url(page: Int = 1) = HttpRequest(uri = s"${mainGithubUrl(ref)}/issues?${perPage()}&page=$page")

    def getOpenIssuePage(page: Int): Future[Seq[GithubModel.OpenIssue]] = {
      val request = url(page).addHeader(acceptJson).addCredentials(credentials)
      get[Seq[Option[GithubModel.OpenIssue]]](request).map(_.flatten)
    }

    getRaw(url(1)).failWithTry.flatMap {
      case Success((headers, entity)) =>
        val lastPage = headers.find(_.is("link")).map(_.value()).flatMap(extractLastPage)
        lastPage match {
          case Some(lastPage) if lastPage > 1 =>
            for {
              page1 <- Unmarshal(entity).to[Seq[Option[GithubModel.OpenIssue]]]
              nextPages <- (2 to lastPage).mapSync(getOpenIssuePage).map(_.flatten)
            } yield page1.flatten ++ nextPages

          case _ => Unmarshal(entity).to[Seq[Option[GithubModel.OpenIssue]]].map(_.flatten)
        }
      case Failure(exception) =>
        // the issue feature can be disabled
        Future.successful(Seq.empty)
    }
  }

  def getGiterChatRoom(ref: Project.Reference): Future[Option[String]] = {
    val url = s"https://gitter.im/${ref.organization}/${ref.repository}"
    val httpRequest = HttpRequest(uri = s"https://gitter.im/${ref.organization}/${ref.repository}")
    queueRequest(httpRequest).map {
      case _ @HttpResponse(StatusCodes.OK, _, _, _) => Some(url)
      case _                                        => None
    }
  }

  def fetchReposUnderUserOrganizations(
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

    val request = graphqlRequest(token, query)
    val githubRepoPage1 = get[List[GithubModel.RepoWithPermissionPage]](request)
    if (filterPermissions.isEmpty) githubRepoPage1.map(_.flatMap(_.toGithubRepos))
    else
      githubRepoPage1.map(_.flatMap(_.toGithubReposWithPermission).collect {
        case (repo, permission) if filterPermissions.contains(permission) => repo
      })

  }
  def fetchUserRepo(login: String, filterPermissions: Seq[String]): Future[Seq[Project.Reference]] = {
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

    val request = graphqlRequest(token, query(None))
    val page1 = get[GithubModel.RepoWithPermissionPage](request)(GithubModel.decoderForUserRepo)
    def getPageRecursively(
        pageFut: Future[GithubModel.RepoWithPermissionPage]
    )(previous: Future[Seq[GithubModel.RepoWithPermissionPage]]): Future[Seq[GithubModel.RepoWithPermissionPage]] =
      pageFut.flatMap { page =>
        if (!page.hasNextPage) previous.map(page +: _)
        else {
          val nextPage = get[GithubModel.RepoWithPermissionPage](graphqlRequest(token, query(Some(page.endCursor))))(
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

  def fetchUser(): Future[UserInfo] = {
    val query =
      """|query {
         |  viewer {
         |    login
         |    avatarUrl
         |    name
         |  }
         |}""".stripMargin
    val request = graphqlRequest(token, query)
    val userInfo = get[GithubModel.UserInfo](request)

    userInfo.map(_.toCoreUserInfo(token))
  }

  // only the first 100 orgs
  def fetchUserOrganizations(login: String): Future[Set[Project.Organization]] = {
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
    val request = graphqlRequest(token, query)
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

  // when a token is provided, we don't use the queuing system
  private def graphqlRequest(token: Secret, query: String): HttpRequest = {
    val json = Map("query" -> query.asJson).asJson
    HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("https://api.github.com/graphql"),
      entity = HttpEntity(ContentTypes.`application/json`, json.toString()),
      headers = List(Authorization(OAuth2BearerToken(token.decode)))
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

  private def process(request: HttpRequest): Future[GithubResponse[(Seq[HttpHeader], ResponseEntity)]] =
    queueRequest(request).flatMap {
      case r @ HttpResponse(StatusCodes.OK, headers, entity, _) =>
        Future.successful(GithubResponse.Ok((headers, entity)))
      case r @ HttpResponse(StatusCodes.MovedPermanently, headers, entity, _) =>
        entity.discardBytes()
        val newRequest = HttpRequest(uri = headers.find(_.is("location")).get.value())
        process(newRequest).map {
          case GithubResponse.Ok(res)                    => GithubResponse.MovedPermanently(res)
          case GithubResponse.Failed(code, errorMessage) => GithubResponse.Failed(code, errorMessage)
          case GithubResponse.MovedPermanently(res)      => GithubResponse.MovedPermanently(res)
        }
      case _ @HttpResponse(code, _, entity, _) =>
        entity.discardBytes()
        Future.successful(GithubResponse.Failed(code.intValue, code.reason()))
    }

  private def mainGithubUrl(ref: Project.Reference): String =
    s"https://api.github.com/repos/${ref.organization}/${ref.repository}"

  private def inPage(page: Int = 1): String =
    s"page=$page"

  private def perPage(value: Int = 100) = s"per_page=$value"
}

object GithubClient {}
