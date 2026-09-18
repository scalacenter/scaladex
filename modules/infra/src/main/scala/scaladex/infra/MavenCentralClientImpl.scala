package scaladex.infra

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.SbtPlugin
import scaladex.core.model.Version
import scaladex.core.service.MavenCentralClient
import scaladex.core.util.JsoupUtils
import scaladex.infra.config.HttpClientConfig

import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.settings.ConnectionPoolSettings
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.apache.pekko.util.ByteString

class MavenCentralClientImpl(httpClient: CommonAkkaHttpClient)(using system: ActorSystem)
    extends MavenCentralClient
    with LazyLogging:
  private given ExecutionContextExecutor = system.dispatcher
  private val baseUri = "https://repo1.maven.org/maven2"

  def getAllArtifactIds(groupId: Artifact.GroupId): Future[Seq[Artifact.ArtifactId]] =
    val uri = s"$baseUri/${groupId.mavenUrl}/"
    val request =
      HttpRequest(uri = uri)

    for
      response <- httpClient.queueRequestWithRetry(request)
      directories <- listDirectories(uri, response)
    yield directories.map(Artifact.ArtifactId.apply)
  end getAllArtifactIds

  def getAllVersions(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Version]] =
    val uri = s"$baseUri/${groupId.mavenUrl}/${artifactId.value}/maven-metadata.xml"
    for
      response <- httpClient.queueRequestWithRetry(HttpRequest(uri = uri))
      versions <- parseMavenMetadata(uri, response)
    yield versions
  end getAllVersions

  override def getPomFile(ref: Artifact.Reference): Future[(String, Instant)] =
    val pomUri = getPomUri(ref)
    for
      response <- httpClient.queueRequestWithRetry(HttpRequest(uri = pomUri))
      res <- getPomFileWithLastModifiedTime(response, pomUri)
    yield res
  end getPomFile

  override def getJavadocJar(ref: Artifact.Reference): Future[Option[Array[Byte]]] =
    val jarUri = getJavadocJarUri(ref)
    for
      response <- httpClient.queueRequestWithRetry(HttpRequest(uri = jarUri))
      res <- getJarBytes(response, jarUri)
    yield res
  end getJavadocJar

  private def getJarBytes(response: HttpResponse, uri: String): Future[Option[Array[Byte]]] =
    response.status match
      case StatusCodes.OK =>
        response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(bytes => Some(bytes.toArray))
      case StatusCodes.NotFound =>
        response.discardEntityBytes()
        Future.successful(None)
      case status =>
        response.discardEntityBytes()
        Future.failed(new Exception(s"Cannot get $uri: $status"))

  private def getJavadocJarUri(ref: Artifact.Reference): String =
    val jarFileName = getJavadocJarFileName(ref.artifactId, ref.version)
    s"$baseUri/${ref.groupId.mavenUrl}/${ref.artifactId.value}/${ref.version.value}/$jarFileName"

  private def getJavadocJarFileName(artifactId: Artifact.ArtifactId, version: Version) = artifactId match
    case Artifact.ArtifactId(name, BinaryVersion(SbtPlugin(Version.Minor(0, 13)), _)) =>
      s"$name-${version.value}-javadoc.jar"
    case _ => s"${artifactId.value}-${version.value}-javadoc.jar"

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

  private def parseMavenMetadata(uri: String, response: HttpResponse): Future[Seq[Version]] =
    response.status match
      case StatusCodes.OK =>
        Unmarshaller
          .stringUnmarshaller(response.entity)
          .map(metadata => JsoupUtils.listVersions(metadata).map(Version.apply))
      case StatusCodes.NotFound =>
        response.discardEntityBytes()
        Future.successful(Seq.empty)
      case status =>
        response.discardEntityBytes()
        Future.failed(new Exception(s"Cannot get $uri: $status"))
  end parseMavenMetadata

  private def preview(page: String): String =
    page.iterator.take(200).mkString.replaceAll("\\s+", " ")

  private def getPomUri(ref: Artifact.Reference): String =
    val pomFileName = getPomFileName(ref.artifactId, ref.version)
    s"$baseUri/${ref.groupId.mavenUrl}/${ref.artifactId.value}/${ref.version.value}/$pomFileName"

  // Wed, 04 Nov 2020 23:36:02 GMT
  private val dateFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
  private[infra] def parseDate(dateStr: String): Instant = ZonedDateTime.parse(dateStr, dateFormatter).toInstant

  private def getPomFileName(artifactId: Artifact.ArtifactId, version: Version): String =
    artifactId.binaryVersion.platform match
      case SbtPlugin(Version.Minor(0, 13)) => s"${artifactId.name.value}-${version.value}.pom"
      case _ => s"${artifactId.value}-${version.value}.pom"
end MavenCentralClientImpl

object MavenCentralClientImpl:
  private val poolSettings: ConnectionPoolSettings = ConnectionPoolSettings("").withMaxConnections(10)

  def apply(config: HttpClientConfig = HttpClientConfig.default)(using ActorSystem): MavenCentralClientImpl =
    new MavenCentralClientImpl(new CommonAkkaHttpClient(poolSettings, config))
end MavenCentralClientImpl
