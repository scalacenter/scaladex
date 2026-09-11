package scaladex.infra

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Try

import scaladex.core.model.Artifact
import scaladex.core.model.SbtPlugin
import scaladex.core.model.Version
import scaladex.core.service.MavenCentralClient
import scaladex.core.util.JsoupUtils
import scaladex.infra.config.HttpClientConfig

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

class MavenCentralClientImpl(config: HttpClientConfig = HttpClientConfig.default)(using system: ActorSystem)
    extends CommonAkkaHttpClient(config)
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
      response <- queueRequestWithRetry(request)
      directories <- listDirectories(uri, response)
    yield directories.map(Artifact.ArtifactId.apply)
  end getAllArtifactIds

  def getAllVersions(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Version]] =
    val uri = s"$baseUri/${groupId.mavenUrl}/${artifactId.value}/"
    val request = HttpRequest(uri = uri)
    for
      response <- queueRequestWithRetry(request)
      directories <- listDirectories(uri, response)
    yield directories.map(Version.apply)
  end getAllVersions

  override def getPomFile(ref: Artifact.Reference): Future[(String, Instant)] =
    val pomUri = getPomUri(ref)
    for
      response <- queueRequestWithRetry(HttpRequest(uri = pomUri))
      res <- getPomFileWithLastModifiedTime(response, pomUri)
    yield res
  end getPomFile

  private def getPomFileWithLastModifiedTime(response: HttpResponse, uri: String): Future[(String, Instant)] =
    response match
      case _ @HttpResponse(StatusCodes.OK, headers: Seq[model.HttpHeader], entity, _) =>
        headers.find(_.is("last-modified")).map(header => parseDate(header.value)) match
          case Some(lastModified) =>
            Unmarshaller.stringUnmarshaller(entity).map(page => page -> lastModified)
          case None =>
            entity.discardBytes()
            Future.failed(new Exception(s"Missing last-modified header for $uri"))
      case _ =>
        response.discardEntityBytes()
        Future.failed(new Exception(s"Cannot get $uri: ${response.status}"))

  private def listDirectories(uri: String, response: HttpResponse): Future[Seq[String]] =
    response.status match
      case StatusCodes.OK =>
        Unmarshaller
          .stringUnmarshaller(response.entity)
          .map(page =>
            val directories = JsoupUtils.listDirectories(uri, page)
            if directories.isEmpty then
              logger.warn(s"No directories parsed from $uri (HTTP ${response.status}): ${preview(page)}")
            directories
          )
      case StatusCodes.NotFound =>
        response.discardEntityBytes()
        Future.successful(Seq.empty)
      case status =>
        response.discardEntityBytes()
        Future.failed(new Exception(s"Cannot list $uri: $status"))
  end listDirectories

  private def preview(page: String): String =
    page.iterator.take(200).mkString.replaceAll("\\s+", " ")

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
