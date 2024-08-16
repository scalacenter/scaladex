package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.Artifact
import scaladex.core.model.Artifact._
import scaladex.core.service.SchedulerDatabase
import scaladex.core.service.SonatypeClient
import scaladex.core.util.ScalaExtensions._
import scaladex.data.cleanup.NonStandardLib
import scaladex.infra.DataPaths

class SonatypeService(
    dataPaths: DataPaths,
    database: SchedulerDatabase,
    sonatypeService: SonatypeClient,
    publishProcess: PublishProcess
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  def findNonStandard(): Future[String] = {
    val nonStandardLibs = NonStandardLib.load(dataPaths)
    for {
      mavenReferenceFromDatabase <- database.getAllMavenReferences()
      result <- nonStandardLibs.mapSync { lib =>
        val groupId = Artifact.GroupId(lib.groupId)
        // get should not throw: it is a fixed set of artifactIds
        val artifactId = Artifact.ArtifactId.parse(lib.artifactId).get
        findAndIndexMissingArtifacts(groupId, artifactId, mavenReferenceFromDatabase.toSet)
      }
    } yield s"Inserted ${result.sum} missing poms"
  }

  private def findAndIndexMissingArtifacts(
      groupId: GroupId,
      artifactId: ArtifactId,
      knownRefs: Set[MavenReference]
  ): Future[Int] =
    for {
      versions <- sonatypeService.getAllVersions(groupId, artifactId)
      mavenReferences = versions.map(v =>
        MavenReference(groupId = groupId.value, artifactId = artifactId.value, version = v.toString)
      )
      missingVersions = mavenReferences.filterNot(knownRefs)
      _ = if (missingVersions.nonEmpty)
        logger.warn(s"${missingVersions.size} artifacts are missing for ${groupId.value}:${artifactId.value}")
      missingPomFiles <- missingVersions.map(ref => sonatypeService.getPomFile(ref).map(_.map(ref -> _))).sequence
      publishResult <- missingPomFiles.flatten.mapSync {
        case (mavenRef, (pomFile, creationDate)) =>
          publishProcess.publishPom(mavenRef.toString(), pomFile, creationDate, None)
      }
    } yield publishResult.count {
      case PublishResult.Success => true
      case _                     => false
    }

  def findMissing(): Future[String] =
    for {
      mavenReferenceFromDatabase <- database.getAllMavenReferences().map(_.toSet)
      groupIds = mavenReferenceFromDatabase.map(_.groupId).toSeq.sorted.map(Artifact.GroupId)
      // we sort just to estimate through the logs the percentage of progress
      result <- groupIds.mapSync(g => findAndIndexMissingArtifacts(g, None, mavenReferenceFromDatabase))
    } yield s"Inserted ${result.sum} missing poms"

  private def findAndIndexMissingArtifacts(
      groupId: GroupId,
      artifactNameOpt: Option[Artifact.Name],
      knownRefs: Set[MavenReference]
  ): Future[Int] =
    for {
      artifactIds <- sonatypeService.getAllArtifactIds(groupId)
      scalaArtifactIds = artifactIds.filter(artifact =>
        artifactNameOpt.forall(_ == artifact.name) && artifact.isScala && artifact.binaryVersion.isValid
      )
      result <- scalaArtifactIds
        .mapSync(id => findAndIndexMissingArtifacts(groupId, id, knownRefs))
    } yield result.sum

  def syncOne(groupId: GroupId, artifactNameOpt: Option[Artifact.Name]): Future[String] =
    for {
      mavenReferenceFromDatabase <- database.getAllMavenReferences()
      result <- findAndIndexMissingArtifacts(groupId, artifactNameOpt, mavenReferenceFromDatabase.toSet)
    } yield s"Inserted $result poms"

  def updateAllArtifacts(): Future[String] =
    for {
      mavenReferences <- database.getAllMavenReferences()
      _ = logger.info(s"${mavenReferences.size} artifacts will be synced for new metadata.")
      poms <- mavenReferences.map(ref => sonatypeService.getPomFile(ref).map(_.map(ref -> _))).sequence
      _ = logger.info(s"publishing poms now")
      publishResult <- poms.flatten.mapSync {
        case (mavenRef, (pomFile, creationDate)) =>
          publishProcess.publishPom(mavenRef.toString(), pomFile, creationDate, None)
      }
    } yield s"Updated ${publishResult.size} poms"
}
