package scaladex.infra

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Try
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.Artifact.MavenReference
import scaladex.core.model.SbtPlugin
import scaladex.core.model.SemanticVersion
import scaladex.core.service.SonatypeService
import scaladex.core.util.JsoupUtils
import scaladex.core.util.ScalaExtensions._

class SonatypeClient()(implicit val system: ActorSystem)
    extends CommonAkkaHttpClient
    with SonatypeService
    with LazyLogging {
  private implicit val ec: ExecutionContextExecutor = system.dispatcher
  private val sonatypeUri = "https://repo1.maven.org/maven2"
  lazy val poolClientFlow: Flow[
    (HttpRequest, Promise[HttpResponse]),
    (Try[HttpResponse], Promise[HttpResponse]),
    Http.HostConnectionPool
  ] =
    Http()
      .cachedHostConnectionPoolHttps[Promise[HttpResponse]](
        "repo1.maven.org",
        settings = ConnectionPoolSettings("max-open-requests = 32")
      )

  def getAllArtifactIds(groupId: Artifact.GroupId): Future[Seq[Artifact.ArtifactId]] = {
    val uri = s"$sonatypeUri/${groupId.mavenUrl}/"
    val request =
      HttpRequest(uri = uri)

    for {
      responseFuture <- queueRequest(request)
      page <- responseFuture.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
    } yield {
      val artifactIds = JsoupUtils.listDirectories(uri, page)
      artifactIds.flatMap(Artifact.ArtifactId.parse)
    }
  }

  def getAllVersions(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[SemanticVersion]] = {
    val uri = s"$sonatypeUri/${groupId.mavenUrl}/${artifactId.value}/"
    val request = HttpRequest(uri = uri)

    (for {
      responseFuture <- queueRequest(request)
      page <- Unmarshaller.stringUnmarshaller(responseFuture.entity)
      versions = JsoupUtils.listDirectories(uri, page)
      versionParsed = versions.flatMap(SemanticVersion.parse)
    } yield versionParsed)
      .recoverWith {
        case NonFatal(exception) =>
          logger.warn(s"failed to retrieve versions from $uri because ${exception.getMessage}")
          Future.successful(Nil)
      }
  }

  override def getPomFile(mavenReference: Artifact.MavenReference): Future[Option[(String, Instant)]] =
    for {
      response <- getHttpResponse(mavenReference)
      res <- getPomFileWithLastModifiedTime(response)
    } yield res

  override def getReleaseDate(mavenReference: Artifact.MavenReference): Future[Option[Instant]] =
    for {
      response <- getHttpResponse(mavenReference)
      releaseDate = getLastModifiedTime(response)
    } yield releaseDate

  private def getPomFileWithLastModifiedTime(response: HttpResponse): Future[Option[(String, Instant)]] =
    response match {
      case _ @HttpResponse(StatusCodes.OK, headers: Seq[model.HttpHeader], entity, _) =>
        for {
          page <- Unmarshaller.stringUnmarshaller(entity)
        } yield headers
          .find(_.is("last-modified"))
          .map(header => page -> format(header.value))
      case _ => Future.successful(None)
    }

  private def getLastModifiedTime(response: HttpResponse): Option[Instant] = {
    // we discard en the entity because we only need the headers
    response.discardEntityBytes()
    response.headers
      .find(_.is("last-modified"))
      .map(header => format(header.value))
  }

  private def getHttpResponse(mavenReference: MavenReference): Future[HttpResponse] = {
    val groupIdUrl: String = mavenReference.groupId.replace('.', '/')
    for {
      artifactId <- Artifact.ArtifactId.parse(mavenReference.artifactId).toFuture
      pomUrl = getPomUrl(artifactId, mavenReference.version)
      uri = s"$sonatypeUri/${groupIdUrl}/${mavenReference.artifactId}/${mavenReference.version}/$pomUrl"
      request = HttpRequest(uri = uri)
      response <- queueRequest(request)
    } yield response
  }

  // Wed, 04 Nov 2020 23:36:02 GMT
  private val df = DateTimeFormatter
    .ofPattern("E, dd MMM yyyy HH:mm:ss z")

  private def format(i: String) = ZonedDateTime.parse(i, df).toInstant

  private def getPomUrl(artifactId: Artifact.ArtifactId, version: String): String =
    artifactId.binaryVersion.platform match {
      case SbtPlugin(_) => s"${artifactId.name.value}-$version.pom"
      case _            => s"${artifactId.value}-${version}.pom"
    }
}
