package ch.epfl.scala.index.data.bintray

import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneOffset}

import akka.actor.ActorSystem
import akka.stream.Materializer
import ch.epfl.scala.index.data.DataPaths
import com.typesafe.scalalogging.LazyLogging
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.matching.Regex

class UpdateBintraySbtPlugins(
    bintray: BintrayClient,
    sbtPluginRepo: SbtPluginsData,
    lastDownloadPath: Path
)(implicit val ec: ExecutionContext)
    extends LazyLogging {
  private val dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
  private val moduleDescriptorParser = XmlModuleDescriptorParser.getInstance()

  private val baseSubject = "sbt"
  private val baseRepo = "sbt-plugin-releases"

  /** Matches an ivy.xml file of an sbt-plugin release
   * <organization>/<artifact>/scala_<version>/sbt_<version>/<version>/ivys/ivy.xml
   */
  private val sbtPluginPathRegex: Regex =
    "(.+?)/(.+?)/scala_(.+?)/sbt_(.+?)/(.+?)/ivys/ivy.xml".r

  def update(): Future[Unit] = {
    val lastUpdate = parse(Files.readAllLines(lastDownloadPath).get(0))

    val oldReleasesF = Future(sbtPluginRepo.read())

    // download all the new releases that are published or linked
    // to the sbt/sbt-plugin-releases repository
    val newReleasesF = getNewReleases(lastUpdate)

    for {
      oldReleases <- oldReleasesF
      newReleases <- newReleasesF
    } yield {
      sbtPluginRepo.update(oldReleases, newReleases)
      val now = format(OffsetDateTime.now(ZoneOffset.UTC))
      Files.write(lastDownloadPath, Seq(now).asJava)
    }
  }

  private def getNewReleases(
      lastUpdate: OffsetDateTime
  ): Future[Seq[SbtPluginReleaseModel]] = {
    for {
      packageNames <- bintray.getAllPackages(baseSubject, baseRepo)

      _ = println(s"found ${packageNames.size} packages")
      allPackages <- Future.traverse(packageNames)(
        bintray.getPackage(baseSubject, baseRepo, _)
      )

      packagesToUpdate = allPackages.filter(
        p => parse(p.updated).isAfter(lastUpdate)
      )

      // searching for ivys.xml in the sbt/sbt-plugin-releases will not look into the linked packages
      // That is why we group the packages by repositories and then we search in every repository
      packagesByRepo = packagesToUpdate.groupBy(p => (p.owner, p.repo))

      sbtPlugins <- Future
        .traverse(packagesByRepo) {
          case ((owner, repo), packages) =>
            getSbtPlugins(owner, repo, packages, lastUpdate)
        }
        .map(_.flatten.toSeq)

    } yield sbtPlugins
  }

  private def getSbtPlugins(
      owner: String,
      repo: String,
      packages: Seq[BintrayPackage],
      lastUpdate: OffsetDateTime
  ): Future[Seq[SbtPluginReleaseModel]] = {
    println(s"loading ${packages.size} from $owner/$repo")
    val packageNames = packages.map(_.name).toSet
    val createdAfter = format(lastUpdate)
    for {
      allIvyFiles <- bintray.searchFiles(owner, repo, "ivy.xml", createdAfter)
      filteredIvyFiles = allIvyFiles
        .filter(file => packageNames.contains(file.`package`))
      sbtPlugins <- Future
        .traverse(filteredIvyFiles)(
          file => downloadPluginDescriptor(owner, repo, file)
        )
        .map(_.flatten)
    } yield sbtPlugins
  }

  private def downloadPluginDescriptor(
      owner: String,
      repo: String,
      file: BintraySearch
  ): Future[Option[SbtPluginReleaseModel]] = {
    file.path match {
      case sbtPluginPathRegex(org,
                              artifact,
                              scalaVersion,
                              sbtVersion,
                              version) =>
        Future {
          try {
            val descriptor = moduleDescriptorParser.parseDescriptor(
              new IvySettings(),
              bintray.downloadUrl(owner, repo, file.path),
              /* validate =*/ false
            )

            Some {
              SbtPluginReleaseModel(
                owner,
                repo,
                org,
                artifact,
                scalaVersion,
                sbtVersion,
                version,
                file.path,
                file.sha1,
                descriptor
              )
            }
          } catch {
            case NonFatal(cause) =>
              logger.error(s"Unable to fetch ivy.xml ${file.path}", cause)
              None
          }
        }

      case _ => Future.successful(None)
    }
  }

  private def parse(dateTime: String): OffsetDateTime =
    OffsetDateTime.from(dateTimeFormatter.parse(dateTime))
  private def format(dateTime: OffsetDateTime): String =
    dateTimeFormatter.format(dateTime)

}

object UpdateBintraySbtPlugins {
  /**
   * Update the Scaladex data directory about the sbt plugin releases that have been
   * published to the sbt/sbt-plugin-releases Bintray repository since last update.
   *
   * This corresponds to the 'sbt' step of [[ch.epfl.scala.index.data.Main]]
   * It does not anymore save the ivys.xml files to the data directory.
   *
   * @param paths Paths to the data directory
   */
  def run(paths: DataPaths)(implicit mat: Materializer,
                            sys: ActorSystem): Unit = {
    for (bintrayClient <- BintrayClient.create(paths.credentials)) {
      val sbtPluginsData = SbtPluginsData(paths.ivysData)
      val updater =
        new UpdateBintraySbtPlugins(bintrayClient,
                                    sbtPluginsData,
                                    paths.ivysLastDownload)(sys.dispatcher)
      Await.result(updater.update(), Duration.Inf)
    }
  }
}
