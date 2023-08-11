package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.Artifact._
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SonatypeClient
import scaladex.core.util.ScalaExtensions._

class SonatypeService(
    database: SchedulerDatabase,
    sonatypeService: SonatypeClient,
    publishProcess: PublishProcess
)(implicit ec: ExecutionContext)
    extends LazyLogging {
  import SonatypeService._

  def findMissing(): Future[String] =
    for {
      groupIds <- database.getAllGroupIds()
      // we sort just to estimate through the logs the percentage of progress
      result <- groupIds.sortBy(_.value).mapSync(g => findAndIndexMissingArtifacts(g, None))
    } yield s"Inserted ${result.size} missing poms"

  def syncOne(groupId: GroupId, artifactNameOpt: Option[Artifact.Name]): Future[String] =
    for {
      result <- findAndIndexMissingArtifacts(groupId, artifactNameOpt)
    } yield s"Inserted ${result} poms"

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
      missingPomFiles <- missingVersions.map(ref => sonatypeService.getPomFile(ref).map(_.map(ref -> _))).sequence
      publishResult <- missingPomFiles.flatten.mapSync {
        case (mavenRef, (pomFile, creationDate)) =>
          publishProcess.publishPom(mavenRef.toString, pomFile, creationDate, None)
      }
    } yield publishResult.count {
      case PublishResult.Success => true
      case _                     => false
    }

}

object SonatypeService {
  def findMissingVersions(fromDatabase: Set[MavenReference], fromSonatype: Seq[MavenReference]): Seq[MavenReference] =
    fromSonatype.filterNot(fromDatabase)
}
