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
import scaladex.core.service.SonatypeClient
import scaladex.core.util.JsoupUtils

class SonatypeClientImpl()(implicit val system: ActorSystem)
    extends CommonAkkaHttpClient
    with SonatypeClient
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
      responseOpt <- getHttpResponse(mavenReference)
      res <- responseOpt.map(getPomFileWithLastModifiedTime).getOrElse(Future.successful(None))
    } yield res

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

  private def getHttpResponse(mavenReference: MavenReference): Future[Option[HttpResponse]] = {
    val groupIdUrl: String = mavenReference.groupId.replace('.', '/')
    Artifact.ArtifactId
      .parse(mavenReference.artifactId)
      .map { artifactId =>
        val pomUrl = getPomUrl(artifactId, mavenReference.version)
        val uri = s"$sonatypeUri/${groupIdUrl}/${mavenReference.artifactId}/${mavenReference.version}/$pomUrl"
        val request = HttpRequest(uri = uri)
        queueRequest(request).map(Option.apply)
      }
      .getOrElse {
        logger.info(s"not able to parse ${mavenReference.artifactId} as ArtifactId")
        Future.successful(None)
      }
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
