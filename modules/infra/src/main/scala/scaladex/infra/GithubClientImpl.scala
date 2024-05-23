package scaladex.infra

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.util.Try

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.syntax._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.HttpHeader
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.ResponseEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.headers.Authorization
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString
import scaladex.core.model.GithubCommitActivity
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubResponse
import scaladex.core.model.License
import scaladex.core.model.Project
import scaladex.core.model.Url
import scaladex.core.model.UserInfo
import scaladex.core.model.UserState
import scaladex.core.service.GithubClient
import scaladex.core.util.ScalaExtensions._
import scaladex.core.util.Secret
import scaladex.infra.github.GithubModel
import scaladex.infra.github.GithubModel._

class GithubClientImpl(token: Secret)(implicit val system: ActorSystem)
    extends CommonAkkaHttpClient
    with GithubClient
    with LazyLogging {
  private val credentials: OAuth2BearerToken = OAuth2BearerToken(token.decode)
  private val acceptJson = RawHeader("Accept", "application/vnd.github.v3+json")
  private val acceptHtmlVersion = RawHeader("Accept", "application/vnd.github.VERSION.html")

  private implicit val ec: ExecutionContextExecutor = system.dispatcher
  lazy val poolClientFlow: Flow[
    (HttpRequest, Promise[HttpResponse]),
    (Try[HttpResponse], Promise[HttpResponse]),
    Http.HostConnectionPool
  ] =
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
      scalaPercentage <- getPercentageOfLanguage(repo.ref, language = "Scala")
      commitActivity <- getCommitActivity(repo.ref)
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
      openIssues = openIssues.map(_.toGithubIssue).toList,
      scalaPercentage = Option(scalaPercentage),
      license = repo.licenseName.flatMap(License.get),
      commitActivity = commitActivity
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

  def getPercentageOfLanguage(ref: Project.Reference, language: String): Future[Int] = {
    def toPercentage(portion: Int, total: Int): Int =
      ((portion.toFloat / total) * 100).toInt
    val request =
      HttpRequest(uri = s"${repoUrl(ref)}/languages").addHeader(acceptJson).addCredentials(credentials)
    get[Map[String, Int]](request).map { response =>
      val totalNumBytes = response.values.sum
      response.get(language).fold(0)(toPercentage(_, totalNumBytes))
    }
  }

  def getCommitActivity(ref: Project.Reference): Future[Seq[GithubCommitActivity]] = {
    val request =
      HttpRequest(uri = s"${repoUrl(ref)}/stats/commit_activity").addHeader(acceptJson).addCredentials(credentials)
    get[Seq[GithubCommitActivity]](request).fallbackTo(Future.successful(Seq.empty))
  }

  def getUserOrganizations(user: String): Future[Seq[Project.Organization]] =
    getAllRecursively(getUserOrganizationsPage(user))

  def getUserRepositories(user: String, filterPermissions: Seq[String]): Future[Seq[Project.Reference]] =
    for (repos <- getAllRecursively(getUserRepositoriesPage(user)))
      yield {
        val filtered =
          if (filterPermissions.isEmpty) repos
          else repos.filter(repo => filterPermissions.contains(repo.viewerPermission))
        filtered.map(repo => Project.Reference.from(repo.nameWithOwner))
      }

  def getOrganizationRepositories(
      user: String,
      organization: Project.Organization,
      filterPermissions: Seq[String]
  ): Future[Seq[Project.Reference]] =
    for (repos <- getAllRecursively(getOrganizationProjectsPage(user, organization)))
      yield {
        val filtered =
          if (filterPermissions.isEmpty) repos
          else repos.filter(repo => filterPermissions.contains(repo.viewerPermission))
        filtered.map(repo => Project.Reference.from(repo.nameWithOwner))
      }

  private def getUserOrganizationsPage(
      user: String
  )(cursor: Option[String]): Future[GraphQLPage[Project.Organization]] = {
    val after = cursor.map(c => s"""after: "$c"""").getOrElse("")
    val query =
      s"""|query { 
          |  user(login: "$user") {
          |    organizations(first: 100, $after) {
          |      pageInfo {
          |        endCursor
          |        hasNextPage
          |      }
          |      nodes {
          |				 login
          |      }
          |    }
          |  }
          }""".stripMargin
    val request = graphqlRequest(query)
    get[GraphQLPage[Project.Organization]](request)(
      graphqlPageDecoder("data", "user", "organizations") { d =>
        d.downField("login").as[String].map(Project.Organization.apply)
      }
    )
  }

  private def getUserRepositoriesPage(
      login: String
  )(cursor: Option[String]): Future[GraphQLPage[RepoWithPermission]] = {
    val after = cursor.map(c => s"""after: "$c"""").getOrElse("")
    val query =
      s"""|query {
          |  user(login: "$login") {
          |    repositories(first: 100, $after) {
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
          |""".stripMargin
    val request = graphqlRequest(query)
    get[GraphQLPage[RepoWithPermission]](request)(graphqlPageDecoder("data", "user", "repositories"))
  }

  private def getOrganizationProjectsPage(user: String, organization: Project.Organization)(
      cursor: Option[String]
  ): Future[GraphQLPage[RepoWithPermission]] = {
    val after = cursor.map(c => s"""after: "$c"""").getOrElse("")
    val query =
      s"""|query {
          |  user(login: "$user") {
          |    organization(login: "$organization") {
          |      repositories(first: 100, $after) {
          |        pageInfo {
          |          endCursor
          |          hasNextPage
          |        }
          |        nodes {
          |          nameWithOwner
          |          viewerPermission
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
    val request = graphqlRequest(query)
    get[GraphQLPage[RepoWithPermission]](request)(graphqlPageDecoder("data", "user", "organization", "repositories"))
  }

  private def getAllRecursively[T](f: Option[String] => Future[GraphQLPage[T]]): Future[Seq[T]] = {
    def recurse(cursor: Option[String], acc: Seq[T]): Future[Seq[T]] =
      for {
        currentPage <- f(cursor)
        all <-
          if (currentPage.hasNextPage) recurse(currentPage.endCursor, acc ++ currentPage.nodes)
          else Future.successful(acc ++ currentPage.nodes)
      } yield all
    recurse(None, Nil)
  }

  override def getUserState(): Future[GithubResponse[UserState]] =
    getUserInfo().flatMap {
      case GithubResponse.Ok(info)               => getUserState(info).map(GithubResponse.Ok.apply)
      case GithubResponse.MovedPermanently(info) => getUserState(info).map(GithubResponse.MovedPermanently.apply)
      case failed: GithubResponse.Failed         => Future.successful(failed)
    }

  private def getUserState(userInfo: UserInfo): Future[UserState] = {
    val permissions = Seq("WRITE", "MAINTAIN", "ADMIN")
    for {
      organizations <- getUserOrganizations(userInfo.login)
      organizationRepos <- organizations.flatTraverse { org =>
        getOrganizationRepositories(userInfo.login, org, permissions)
      }
      userRepos <- getUserRepositories(userInfo.login, permissions)
    } yield UserState(repos = organizationRepos.toSet ++ userRepos, orgs = organizations.toSet, info = userInfo)
  }

  def getUserInfo(): Future[GithubResponse[UserInfo]] = {
    val query =
      """|query {
         |  viewer {
         |    login
         |    avatarUrl
         |    name
         |  }
         |}""".stripMargin
    val request = graphqlRequest(query)
    process(request).flatMap {
      case GithubResponse.Ok((_, entity)) =>
        Unmarshal(entity).to[GithubModel.UserInfo].map(res => GithubResponse.Ok(res.toCoreUserInfo(token)))
      case GithubResponse.MovedPermanently((_, entity)) =>
        Unmarshal(entity).to[GithubModel.UserInfo].map(res => GithubResponse.Ok(res.toCoreUserInfo(token)))
      case GithubResponse.Failed(code, errorMessage) =>
        Future.successful(GithubResponse.Failed(code, errorMessage))
    }
  }

  private def extractLastPage(links: String): Option[Int] = {
    val pattern = """page=([0-9]+)>; rel="?last"?""".r
    pattern
      .findFirstMatchIn(links)
      .flatMap(mtch => Try(mtch.group(1).toInt).toOption)
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
      case HttpResponse(StatusCodes.OK, headers, entity, _) =>
        Future.successful(GithubResponse.Ok((headers, entity)))
      case HttpResponse(StatusCodes.MovedPermanently, headers, entity, _) =>
        entity.discardBytes()
        val newRequest = HttpRequest(uri = headers.find(_.is("location")).get.value()).withHeaders(request.headers)
        process(newRequest).map {
          case GithubResponse.Ok(res) => GithubResponse.MovedPermanently(res)
          case other                  => other
        }
      case _ @HttpResponse(code, _, entity, _) =>
        if (entity.contentType.mediaType.isApplication) { // we need to parse as json when the mediaType is application/json
          Unmarshal(entity).to[Json].map(errorMessage => GithubResponse.Failed(code.intValue, errorMessage.toString()))
        } else Unmarshal(entity).to[String].map(errorMessage => GithubResponse.Failed(code.intValue, errorMessage))
    }
  }

  private def repoUrl(ref: Project.Reference): String =
    s"https://api.github.com/repos/$ref"

  private def perPage(value: Int = 100) = s"per_page=$value"
}
