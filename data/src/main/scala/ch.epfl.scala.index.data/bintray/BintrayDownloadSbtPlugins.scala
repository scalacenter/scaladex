package ch.epfl.scala.index.data
package bintray

import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.Materializer
import ch.epfl.scala.index.data.download.PlayWsDownloader
import ch.epfl.scala.index.data.maven._
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.JsonAST.JValue
import org.slf4j.LoggerFactory
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/**
 * Fetches sbt plugins information
 */
final class BintrayDownloadSbtPlugins(
    paths: DataPaths,
    bintrayClient: BintrayClient
)(implicit val materializer: Materializer, val system: ActorSystem)
    extends PlayWsDownloader
    with BintrayProtocol {

  import bintrayClient._
  import system.dispatcher

  assert(bintrayCredentials.nonEmpty, "this steps requires bintray user")

  import BintrayDownloadSbtPlugins._

  private val log = LoggerFactory.getLogger(this.getClass)

  private val ivysData = SbtPluginsData(paths)

  // TODO Define this list in the config
  val pluginsRepositories =
    List(
      "sbt" -> "sbt-plugin-releases"
    )

  /** Matches an ivy.xml file that describes the release of an sbt-plugin */
  // <organization>/<artifact>/scala_<version>/sbt_<version>/<version>/ivys/ivy.xml
  private val sbtPluginPath =
    "(.+?)/(.+?)/scala_(.+?)/sbt_(.+?)/(.+?)/ivys/ivy.xml".r

  def run(): Unit = {
    Await.ready(downloadSbtPlugins(), Duration.Inf)
    ()
  }

  /**
   * Downloads all sbt plugin releases from bintray.
   * If some releases have already been downloaded in the past, only newer releases are downloaded.
   */
  def downloadSbtPlugins(): Future[_] =
    for {
      (oldReleases, lastDownload) <- Future.successful(ivysData.read())
      newReleases <- fetchNewReleases(lastDownload)
    } yield {
      ivysData.update(oldReleases, newReleases.map(parseRelease))
    }

  private def parseRelease(
      release: SbtPluginReleaseWithModuleDescriptor
  ): SbtPluginReleaseModel = {
    val descriptor = release.moduleDescriptor
    val releaseModel =
      ReleaseModel(
        groupId = release.sbtRelease.organization,
        artifactId = release.sbtRelease.artifact,
        version = release.sbtRelease.version,
        packaging = "jar",
        name = None,
        description = Option(descriptor.getDescription),
        inceptionYear = None,
        url = None,
        scm = None,
        issueManagement = None,
        mailingLists = Nil,
        contributors = Nil,
        developers = Nil,
        licenses = Nil,
        dependencies = descriptor.getDependencies.to[List].map { dependency =>
          val dep = dependency.getDependencyRevisionId
          Dependency(
            dep.getOrganisation,
            dep.getName,
            dep.getRevision,
            scope = /* TODO */ None,
            exclusions = dependency.getAllExcludeRules
              .map { rule =>
                Exclusion(rule.getId.getModuleId.getOrganisation,
                          rule.getId.getModuleId.getName)
              }
              .to[Set]
          )
        },
        repositories = Nil,
        organization = None,
        sbtPluginTarget = Some(
          SbtPluginTarget(
            release.sbtRelease.scalaVersion,
            release.sbtRelease.sbtVersion
          )
        )
      )

    val publicationDate =
      new DateTime(
        Option(descriptor.getPublicationDate)
          .getOrElse(sys.error("Missing publication date"))
      ).toString
    SbtPluginReleaseModel(releaseModel,
                          publicationDate,
                          release.sbtRelease.sha1)
  }

  private def fetchNewReleases(
      lastDownload: Option[String]
  ): Future[List[SbtPluginReleaseWithModuleDescriptor]] =
    managed { client =>
      val lastDownload =
        Try(new String(Files.readAllBytes(paths.ivysLastDownload))).toOption

      Future
        .traverse(pluginsRepositories)(
          (listSbtPluginReleases(client, lastDownload) _).tupled
        )
        .map(_.flatten) // Concatenate the plugins of all the `pluginRepositories`

    // TODO Check sha1

    }.andThen {
      case Success(newReleases) =>
        writeSbtPluginReleases(newReleases)
    }

  /**
   * @return All the sbt plugin releases found in the given repository
   * @param client  HTTP client
   * @param subject User or organization name (e.g. "sbt")
   * @param repo    Repository name (e.g. "sbt-plugin-releases")
   */
  private def listSbtPluginReleases(client: WSClient,
                                    lastDownload: Option[String])(
      subject: String,
      repo: String
  ): Future[List[SbtPluginReleaseWithModuleDescriptor]] = {

    def fetchPage(startPos: Int): Future[WSResponse] = {

      val parameters =
        List(
          "name" -> "ivy.xml", // Releases produce an “ivy.xml” file
          "subject" -> subject,
          "repo" -> repo,
          "start_pos" -> startPos.toString
        )

      val createdAfterParameter =
        lastDownload match {
          case Some(date) => List("created_after" -> date)
          case _          => List()
        }

      val allParameters = parameters ::: createdAfterParameter

      val request =
        client
          .url(s"$apiUrl/search/file")
          .withQueryStringParameters(allParameters: _*)

      withAuth(request).get()
    }

    val decode: WSResponse => List[SbtPluginRelease] =
      decodeSucessfulJson { json =>
        json
          .extract[List[JValue]]
          .map { json =>
            ((json \ "path").extract[String], (json \ "sha1").extract[String])
          }
          // We filter the results because not all the “ivy.xml” files found describe sbt-plugin releases
          .collect {
            case (path @ sbtPluginPath(org,
                                       artifact,
                                       scalaVersion,
                                       sbtVersion,
                                       version),
                  sha1) =>
              SbtPluginRelease(subject,
                               repo,
                               org,
                               artifact,
                               scalaVersion,
                               sbtVersion,
                               version,
                               path,
                               sha1)
          }
      }

    fetchPaginatedResource(fetchPage)(decode)
      .flatMap(sequentiallyFetchModuleDescriptors(client, subject, repo))
  }

  // **sequentially** downloads module descriptors (if we do it in parallel, bintray rejects us)
  private def sequentiallyFetchModuleDescriptors(
      client: WSClient,
      subject: String,
      repo: String
  )(
      releases: Seq[SbtPluginRelease]
  ): Future[List[SbtPluginReleaseWithModuleDescriptor]] = {

    val count = releases.size
    val maybeProgress =
      if (count > 1)
        Some(
          ProgressBar(s"Downloading descriptors of $subject/$repo", count, log)
        )
      else None

    maybeProgress.foreach(_.start())

    releases
      .foldLeft(
        Future
          .successful(List.newBuilder[SbtPluginReleaseWithModuleDescriptor])
      ) {
        case (eventuallyReleases, release) =>
          for {
            previousReleases <- eventuallyReleases // The order of these two lines is important!
            moduleDescriptor <- fetchModuleDescriptor(client)(release)
            _ = maybeProgress.foreach(_.step())
          } yield
            previousReleases += SbtPluginReleaseWithModuleDescriptor(
              release,
              moduleDescriptor
            )
      }
      .map { builder =>
        maybeProgress.foreach(_.stop())
        builder.result()
      }
  }

  /**
   * @return The ivy module descriptor of the given file path
   * @param client HTTP client
   * @param release Release to find the module descriptor for
   */
  private def fetchModuleDescriptor(
      client: WSClient
  )(release: SbtPluginRelease): Future[ModuleDescriptor] =
    Future {
      concurrent.blocking {
        XmlModuleDescriptorParser
          .getInstance()
          .parseDescriptor(
            new IvySettings(),
            downloadUrl(release.subject, release.repo, release.ivyPath),
            /* validate = */ false
          )
      }
    }.andThen {
      case Failure(t) =>
        log.error("Unable to fetch ivy.xml descriptor", t)
    }

  private def writeSbtPluginReleases(
      releases: List[SbtPluginReleaseWithModuleDescriptor]
  ): Unit = {

    val count = releases.size
    val maybeProgress =
      if (count > 1)
        Some(ProgressBar("Writing ivy.xml descriptors", releases.size, log))
      else None

    // --- Store the ivy.xml files
    maybeProgress.foreach(_.start())
    releases.foreach { release =>
      // Each ivy.xml file is stored at “scaladex-index/ivys/<subject>/<repo>/<ivy-path>”
      // e.g. “scaladex-index/ivys/sbt/sbt-plugin-releases/com.github.gseitz/sbt-release/scala_2.10/sbt_0.13/0.8.5/ivys/ivy.xml”
      val ivyPath =
        paths.ivys
          .resolve(release.sbtRelease.subject)
          .resolve(release.sbtRelease.repo)
          .resolve(release.sbtRelease.ivyPath)

      // Save the ivy.xml file that we downloaded
      Files.createDirectories(ivyPath.getParent)
      release.moduleDescriptor.toIvyFile(ivyPath.toFile)

      maybeProgress.foreach(_.step())
    }
    maybeProgress.foreach(_.stop())

    // --- Store the “last-download” file
    val now =
      ISODateTimeFormat.dateTime().print(DateTime.now(DateTimeZone.UTC))
    Files.write(paths.ivysLastDownload, now.getBytes)

    ()
  }
}

object BintrayDownloadSbtPlugins {

  def run(paths: DataPaths)(implicit system: ActorSystem,
                            mat: Materializer): Unit = {
    for (client <- BintrayClient.create(paths.credentials)) {
      val download = new BintrayDownloadSbtPlugins(paths, client)
      download.run()
    }
  }

  /**
   * @param subject User or organization name (e.g. "sbt")
   * @param repo Repository name (e.g. "sbt-plugin-releases")
   * @param organization Organization name of the published artifact (e.g. "com.typesafe.play")
   * @param artifact Artifact name (e.g. "sbt-plugin")
   * @param scalaVersion Scala version (e.g. "2.10")
   * @param sbtVersion Sbt version (e.g. "0.13")
   * @param version Release version (e.g. "1.0.0")
   * @param ivyPath Path of the ivy.xml file
   */
  case class SbtPluginRelease(
      subject: String,
      repo: String,
      organization: String,
      artifact: String,
      scalaVersion: String,
      sbtVersion: String,
      version: String,
      ivyPath: String,
      sha1: String
  )

  case class SbtPluginReleaseWithModuleDescriptor(
      sbtRelease: SbtPluginRelease,
      moduleDescriptor: ModuleDescriptor
  )

}
