package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact._
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SonatypeService
import scaladex.core.util.ScalaExtensions._

class SonatypeSynchronizer(
    database: SchedulerDatabase,
    sonatypeService: SonatypeService,
    publishProcess: PublishProcess
)(implicit ec: ExecutionContext)
    extends Scheduler("sync-sonatype", 24.hours)
    with LazyLogging {
  import SonatypeSynchronizer._
  override def run(): Future[Unit] =
    for {
      groupIds <- database.getAllGroupIds()
      mavenReferenceFromDatabase <- database.getAllMavenReferences()
      // we sort just to estimate through the logs the percentage of progress
      result <- groupIds.sortBy(_.value).mapSync(g => findAndIndexMissingArtifacts(g, mavenReferenceFromDatabase))
      _ = logger.info(s"${result.size} poms have been successfully indexed")
    } yield ()

  def findAndIndexMissingArtifacts(groupId: GroupId, mavenReferenceFromDatabase: Seq[MavenReference]): Future[Int] =
    for {
      artifactIds <- sonatypeService.getAllArtifactIds(groupId)
      scalaArtifactIds = artifactIds.filter(_.isScala)
      result <- scalaArtifactIds
        .map(id => findAndIndexMissingArtifacts(groupId, id, mavenReferenceFromDatabase.toSet))
        .sequence
    } yield result.sum

  private def findAndIndexMissingArtifacts(
      groupId: GroupId,
      artifactId: ArtifactId,
      mavenReferenceFromDatabase: Set[MavenReference]
  ): Future[Int] =
    for {
      versions <- sonatypeService.getAllVersions(groupId, artifactId)
      mavenReferences = versions.map(v =>
        MavenReference(groupId = groupId.value, artifactId = artifactId.value, version = v.toString)
      )
      missingVersions = findMissingVersions(mavenReferenceFromDatabase, mavenReferences)
      _ = if (missingVersions.nonEmpty)
        logger.warn(s"${missingVersions.size} artifacts are missing for ${groupId.value}:${artifactId.value}")
      else ()
      missingPomFiles <- missingVersions.map(v => sonatypeService.getPomFile(v).map(_.map(v -> _))).sequence
      publishResult <- missingPomFiles.flatten.mapSync {
        case (mavenRef, (pomFile, creationDate)) =>
          publishProcess.publishPom(s"${mavenRef}", pomFile, creationDate, None)
      }
    } yield publishResult.count {
      case PublishResult.Success => true
      case _                     => false
    }

}

object SonatypeSynchronizer {
  def findMissingVersions(fromDatabase: Set[MavenReference], fromSonatype: Seq[MavenReference]): Seq[MavenReference] =
    fromSonatype.filterNot(fromDatabase)
}
