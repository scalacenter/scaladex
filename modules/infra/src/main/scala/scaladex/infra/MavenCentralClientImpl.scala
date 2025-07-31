package scaladex.infra

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Try
import scala.util.control.NonFatal

import scaladex.core.model.Artifact
import scaladex.core.model.SbtPlugin
import scaladex.core.model.Version
import scaladex.core.service.MavenCentralClient
import scaladex.core.util.JsoupUtils

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.util.ByteString

class MavenCentralClientImpl()(using system: ActorSystem)
    extends CommonAkkaHttpClient
    with MavenCentralClient
    with LazyLogging:
  private given ExecutionContextExecutor = system.dispatcher
  private val baseUri = "https://repo1.maven.org/maven2"
  override def initPoolClientFlow: Flow[
    (HttpRequest, Promise[HttpResponse]),
    (Try[HttpResponse], Promise[HttpResponse]),
    Http.HostConnectionPool
  ] =
    Http()
      .cachedHostConnectionPoolHttps[Promise[HttpResponse]](
        "repo1.maven.org",
        settings = ConnectionPoolSettings("max-open-requests = 32")
      )

  def getAllArtifactIds(groupId: Artifact.GroupId): Future[Seq[Artifact.ArtifactId]] =
    val uri = s"$baseUri/${groupId.mavenUrl}/"
    val request =
      HttpRequest(uri = uri)

    for
      responseFuture <- queueRequest(request)
      page <- responseFuture.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
    yield
      val artifactIds = JsoupUtils.listDirectories(uri, page)
      artifactIds.map(Artifact.ArtifactId.apply)
  end getAllArtifactIds

  def getAllVersions(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Version]] =
    val uri = s"$baseUri/${groupId.mavenUrl}/${artifactId.value}/"
    val request = HttpRequest(uri = uri)

    val future = for
      responseFuture <- queueRequest(request)
      page <- Unmarshaller.stringUnmarshaller(responseFuture.entity)
      versions = JsoupUtils.listDirectories(uri, page)
      versionParsed = versions.map(Version.apply)
    yield versionParsed
    future.recoverWith {
      case NonFatal(exception) =>
        logger.warn(s"failed to retrieve versions from $uri because ${exception.getMessage}")
        Future.successful(Nil)
    }
  end getAllVersions

  override def getPomFile(ref: Artifact.Reference): Future[Option[(String, Instant)]] =
    val pomUri = getPomUri(ref)
    val future = for
      response <- queueRequest(HttpRequest(uri = pomUri))
      res <- getPomFileWithLastModifiedTime(response, pomUri)
    yield res
    future.recoverWith {
      case NonFatal(exception) =>
        logger.warn(s"Could not get pom file of $ref because of $exception")
        Future.successful(None)
    }
  end getPomFile

  private def getPomFileWithLastModifiedTime(response: HttpResponse, uri: String): Future[Option[(String, Instant)]] =
    response match
      case _ @HttpResponse(StatusCodes.OK, headers: Seq[model.HttpHeader], entity, _) =>
        val lastModified = headers.find(_.is("last-modified")).map(header => parseDate(header.value))
        Unmarshaller
          .stringUnmarshaller(entity)
          .map(page => lastModified.map(page -> _))
      case _ =>
        logger.warn(s"Cannot get $uri: ${response.status}")
        Future.successful(None)

  private def getPomUri(ref: Artifact.Reference): String =
    val groupIdUrl: String = ref.groupId.value.replace('.', '/')
    val pomFileName = getPomFileName(ref.artifactId, ref.version)
    s"$baseUri/${groupIdUrl}/${ref.artifactId.value}/${ref.version.value}/$pomFileName"

  // Wed, 04 Nov 2020 23:36:02 GMT
  private val dateFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
  private[infra] def parseDate(dateStr: String): Instant = ZonedDateTime.parse(dateStr, dateFormatter).toInstant

  private def getPomFileName(artifactId: Artifact.ArtifactId, version: Version): String =
    artifactId.binaryVersion.platform match
      case SbtPlugin(Version.Minor(0, 13)) => s"${artifactId.name.value}-${version.value}.pom"
      case _ => s"${artifactId.value}-${version.value}.pom"
end MavenCentralClientImpl
