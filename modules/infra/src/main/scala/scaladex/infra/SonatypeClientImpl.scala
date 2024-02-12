package scaladex.infra

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Try
import scala.util.control.NonFatal

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

    val future = for {
      responseFuture <- queueRequest(request)
      page <- Unmarshaller.stringUnmarshaller(responseFuture.entity)
      versions = JsoupUtils.listDirectories(uri, page)
      versionParsed = versions.flatMap(SemanticVersion.parse)
    } yield versionParsed
    future.recoverWith {
      case NonFatal(exception) =>
        logger.warn(s"failed to retrieve versions from $uri because ${exception.getMessage}")
        Future.successful(Nil)
    }
  }

  override def getPomFile(mavenReference: Artifact.MavenReference): Future[Option[(String, Instant)]] = {
    val future = for {
      responseOpt <- getHttpResponse(mavenReference)
      res <- responseOpt.map(getPomFileWithLastModifiedTime).getOrElse(Future.successful(None))
    } yield res
    future.recoverWith {
      case NonFatal(exception) =>
        logger.warn(s"Could not get pom file of $mavenReference because of $exception")
        Future.successful(None)
    }
  }

  private def getPomFileWithLastModifiedTime(response: HttpResponse): Future[Option[(String, Instant)]] =
    response match {
      case _ @HttpResponse(StatusCodes.OK, headers: Seq[model.HttpHeader], entity, _) =>
        val lastModified = headers.find(_.is("last-modified")).map(header => parseDate(header.value))
        Unmarshaller
          .stringUnmarshaller(entity)
          .map(page => lastModified.map(page -> _))
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
  private val dateFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
  private[infra] def parseDate(dateStr: String): Instant = ZonedDateTime.parse(dateStr, dateFormatter).toInstant

  private def getPomUrl(artifactId: Artifact.ArtifactId, version: String): String =
    artifactId.binaryVersion.platform match {
      case SbtPlugin(_) => s"${artifactId.name.value}-$version.pom"
      case _            => s"${artifactId.value}-${version}.pom"
    }
}
