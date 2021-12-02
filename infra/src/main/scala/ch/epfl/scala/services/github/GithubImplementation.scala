package ch.epfl.scala.services.github

import java.time.Instant

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ResponseEntity
import akka.http.scaladsl.model.StatusCodes
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
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.model.misc.Url
import ch.epfl.scala.services.GithubService
import ch.epfl.scala.services.github.GithubModel.Contributor
import ch.epfl.scala.utils.ScalaExtensions.TraversableOnceFutureExtension
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import ch.epfl.scala.utils.ScalaExtensions._

class GithubImplementation(githubConfig: GithubConfig)(implicit system: ActorSystem) extends GithubService {
  private val credentials = OAuth2BearerToken(githubConfig.token.decode)
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

  val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] = Source
    .queue[(HttpRequest, Promise[HttpResponse])](100, OverflowStrategy.dropNew)
    .via(poolClientFlow)
    .toMat(Sink.foreach {
      case (Success(resp), p) => p.success(resp)
      case (Failure(e), p)    => p.failure(e)
    })(Keep.left)
    .run()

  override def update(repo: GithubRepo): Future[GithubInfo] =
    for {
      repoInfo <- getRepoInfo(repo)
      readme <- getReadme(repo)
      communityProfile <- getCommunityProfile(repo)
      contributors <- getContributors(repo)
      openIssues <- getOpenIssues(repo)
      chatroom <- getGiterChatRoom(repo)
    } yield GithubInfo(
      name = repoInfo.name,
      owner = repoInfo.owner,
      homepage = repoInfo.homepage.map(Url),
      description = repoInfo.description,
      logo = Option(repoInfo.avatartUrl).map(Url),
      stars = Option(repoInfo.stargazers_count),
      forks = Option(repoInfo.forks),
      watchers = Option(repoInfo.subscribers_count),
      issues = Option(repoInfo.open_issues),
      readme = Option(readme),
      contributors = contributors.map(_.toGithubContributor),
      contributorCount = contributors.size,
      commits = Some(contributors.foldLeft(0)(_ + _.contributions)),
      topics = repoInfo.topics.toSet,
      contributingGuide = communityProfile.contributingFile.map(Url),
      codeOfConduct = communityProfile.codeOfConductFile.map(Url),
      chatroom = chatroom.map(Url),
      beginnerIssues = openIssues.map(_.toGithubIssue).toList,
      updatedAt = Instant.now()
    )

  def getReadme(repo: GithubRepo): Future[String] = {
    val request = HttpRequest(uri = s"${mainGithubUrl(repo)}/readme")
      .addCredentials(credentials)
      .addHeader(acceptHtmlVersion)

    send(request).flatMap { case (_, entity) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
    }
  }

  def getCommunityProfile(repo: GithubRepo): Future[GithubModel.CommunityProfile] = {
    val request = HttpRequest(uri = s"${mainGithubUrl(repo)}/community/profile")
      .addCredentials(credentials)
      .addHeader(RawHeader("Accept", "application/vnd.github.black-panther-preview+json"))
    get[GithubModel.CommunityProfile](request)
  }

  def getContributors(repo: GithubRepo): Future[List[Contributor]] = {
    def url(page: Int = 1) = HttpRequest(uri = s"${mainGithubUrl(repo)}/contributors?${perPage()}&${inPage(page)}")

    def getContributionPage(page: Int): Future[List[Contributor]] = {
      val request = url(page).addHeader(acceptJson).addCredentials(credentials)
      get[List[Contributor]](request)
    }

    val firstPage = url().addHeader(acceptJson).addCredentials(credentials)
    send(firstPage).flatMap { case (headers, entity) =>
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

  def getRepoInfo(repo: GithubRepo): Future[GithubModel.Repository] = {
    val request = HttpRequest(uri = s"${mainGithubUrl(repo)}").addHeader(acceptJson).addCredentials(credentials)
    get[GithubModel.Repository](request)
  }

  def getOpenIssues(repo: GithubRepo): Future[Seq[GithubModel.OpenIssue]] = {
    def url(page: Int = 1) = HttpRequest(uri = s"${mainGithubUrl(repo)}/issues?${perPage()}&page=$page")

    def getOpenIssuePage(page: Int): Future[Seq[GithubModel.OpenIssue]] = {
      val request = url(page).addHeader(acceptJson).addCredentials(credentials)
      get[Seq[Option[GithubModel.OpenIssue]]](request).map(_.flatten)
    }
    send(url(1)).flatMap { case (headers, entity) =>
      val lastPage = headers.find(_.is("link")).map(_.value()).flatMap(extractLastPage)
      lastPage match {
        case Some(lastPage) if lastPage > 1 =>
          for {
            page1 <- Unmarshal(entity).to[Seq[Option[GithubModel.OpenIssue]]]
            nextPages <- (2 to lastPage).map(getOpenIssuePage).sequence.map(_.flatten)
          } yield page1.flatten ++ nextPages

        case _ => Unmarshal(entity).to[Seq[Option[GithubModel.OpenIssue]]].map(_.flatten)
      }
    }
  }

  def getGiterChatRoom(repo: GithubRepo): Future[Option[String]] = {
    val url = s"https://gitter.im/${repo.organization}/${repo.repository}"
    val httpRequest = HttpRequest(uri = s"https://gitter.im/${repo.organization}/${repo.repository}")
    queueRequest(httpRequest).map {
      case _ @HttpResponse(StatusCodes.OK, _, _, _) => Some(url)
      case _                                        => None
    }
  }

  private def extractLastPage(links: String): Option[Int] = {
    val pattern = """page=([0-9]+)>; rel="?last"?""".r
    pattern
      .findFirstMatchIn(links)
      .flatMap(mtch => Try(mtch.group(1).toInt).toOption)
  }

  private def get[A](request: HttpRequest)(implicit decoder: io.circe.Decoder[A]): Future[A] =
    send(request).flatMap { case (_, entity) => Unmarshal(entity).to[A] }

  private def send(request: HttpRequest): Future[(Seq[HttpHeader], ResponseEntity)] = {
    queueRequest(request).flatMap {
      case r @ HttpResponse(StatusCodes.OK, headers, entity, _) =>
        Future.successful((headers, entity))
      case r @ HttpResponse(StatusCodes.MovedPermanently, headers, entity, _) =>
        entity.discardBytes()
        val newRequest = HttpRequest(uri = headers.find(_.is("location")).get.value())
        send(newRequest)
      case _ @HttpResponse(code, _, entity, _) =>
        entity.discardBytes()
        Future.failed(new Exception(s"Request failed, response code: $code"))
    }
  }

  private def mainGithubUrl(repo: GithubRepo): String =
    s"https://api.github.com/repos/${repo.organization}/${repo.repository}"

  private def inPage(page: Int = 1): String =
    s"page=$page"

  private def perPage(value: Int = 100) = s"per_page=$value"

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

}
