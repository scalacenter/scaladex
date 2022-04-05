package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.Artifact._
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SonatypeService
import scaladex.core.util.ScalaExtensions._

class SonatypeSynchronizer(
    database: SchedulerDatabase,
    sonatypeService: SonatypeService,
    publishProcess: PublishProcess
)(implicit ec: ExecutionContext)
    extends LazyLogging {
  import SonatypeSynchronizer._

  def syncAll(): Future[Unit] =
    for {
      groupIds <- database.getAllGroupIds()
      // we sort just to estimate through the logs the percentage of progress
      result <- groupIds.sortBy(_.value).mapSync(g => findAndIndexMissingArtifacts(g, None))
      _ = logger.info(s"${result.size} poms have been successfully indexed")
    } yield ()

  def syncOne(groupId: GroupId, artifactNameOpt: Option[Artifact.Name]): Future[Unit] =
    for {
      result <- findAndIndexMissingArtifacts(groupId, artifactNameOpt)
      _ = logger.info(s"${result} poms have been successfully indexed")
    } yield ()

  private def findAndIndexMissingArtifacts(groupId: GroupId, artifactNameOpt: Option[Artifact.Name]): Future[Int] =
    for {
      mavenReferenceFromDatabase <- database.getAllMavenReferences()
      artifactIds <- sonatypeService.getAllArtifactIds(groupId)
      scalaArtifactIds = artifactIds.filter(artifact =>
        artifactNameOpt.forall(_ == artifact.name) && artifact.isScala && artifact.binaryVersion.isValid
      )
      result <- scalaArtifactIds
        .mapSync(id => findAndIndexMissingArtifacts(groupId, id, mavenReferenceFromDatabase.toSet))
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
