package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

import scaladex.core.model.Artifact
import scaladex.core.model.Artifact.*
import scaladex.core.model.Project
import scaladex.core.service.MavenCentralClient
import scaladex.core.service.SchedulerDatabase
import scaladex.core.util.ScalaExtensions.*
import scaladex.data.cleanup.NonStandardLib
import scaladex.infra.DataPaths

import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Timer
import com.typesafe.scalalogging.LazyLogging

class MavenCentralService(
    dataPaths: DataPaths,
    database: SchedulerDatabase,
    mavenCentralClient: MavenCentralClient,
    publishProcess: PublishProcess
)(using ExecutionContext)
    extends LazyLogging:
  private given ContextShift[IO] = IO.contextShift(summon[ExecutionContext])
  private given Timer[IO] = IO.timer(summon[ExecutionContext])

  private val groupIdPageSize = 50
  private val artifactRefPageSize = 1000
  private val artifactIdPageSize = 20
  private val pageDelay = 500.millis
  private val publishDelay = 100.millis

  def findNonStandard(): IO[String] =
    val nonStandardLibs = NonStandardLib.load(dataPaths)
    for result <- nonStandardLibs.mapIO { lib =>
        val groupId = Artifact.GroupId(lib.groupId)
        // get should not throw: it is a fixed set of artifactIds
        val artifactId = Artifact.ArtifactId(lib.artifactId)
        for
          knownRefs <- loadKnownRefs(groupId)
          inserted <- findAndIndexMissingArtifacts(groupId, artifactId, knownRefs)
        yield inserted
      }
    yield s"Inserted ${result.sum} missing poms"
  end findNonStandard

  private def findAndIndexMissingArtifacts(
      groupId: GroupId,
      artifactId: ArtifactId,
      knownRefs: Set[Artifact.Reference]
  ): IO[Int] =
    for
      versions <- mavenCentralClient.getAllVersions(groupId, artifactId).toIO
      missingVersions = versions.map(Artifact.Reference(groupId, artifactId, _)).filterNot(knownRefs)
      _ = if missingVersions.nonEmpty then
        logger.info(s"${missingVersions.size} artifacts are missing for ${groupId.value}:${artifactId.value}")
      missingPomFiles <- missingVersions.mapIO(ref => mavenCentralClient.getPomFile(ref).toIO.map(_.map(ref -> _)))
      publishResult <- missingPomFiles.flatten.mapIO {
        case (mavenRef, (pomFile, creationDate)) =>
          for
            _ <- IO.sleep(publishDelay)
            result <- publishProcess.publishPom(mavenRef.toString(), pomFile, creationDate, None)
          yield result
      }
    yield publishResult.count {
      case PublishResult.Success => true
      case _ => false
    }

  def findMissing(): IO[String] =
    def loop(page: Int, totalInserted: Int): IO[Int] =
      for
        batch <- database.getGroupIds(limit = groupIdPageSize, offset = page * groupIdPageSize)
        _ = logger.info(s"Processing group ID page $page (${batch.size} groups)")
        inserted <- batch.mapIO(g => findAndIndexMissingArtifacts(g, None)).map(_.sum)
        total = totalInserted + inserted
        result <-
          if batch.size == groupIdPageSize then IO.sleep(pageDelay).flatMap(_ => loop(page + 1, total))
          else IO.pure(total)
      yield result

    loop(0, 0).map(n => s"Inserted $n missing poms")
  end findMissing

  private def findAndIndexMissingArtifacts(
      groupId: GroupId,
      artifactNameOpt: Option[Artifact.Name]
  ): IO[Int] =
    for
      knownRefs <- loadKnownRefs(groupId)
      artifactIds <- mavenCentralClient.getAllArtifactIds(groupId).toIO
      scalaArtifactIds = artifactIds.filter(artifact =>
        artifactNameOpt.forall(_ == artifact.name) && artifact.isScala && artifact.binaryVersion.isValid
      )
      result <- processPages(scalaArtifactIds, artifactIdPageSize) { batch =>
        batch.mapIO(id => findAndIndexMissingArtifacts(groupId, id, knownRefs)).map(_.sum)
      }
    yield result

  def syncOne(groupId: GroupId, artifactNameOpt: Option[Artifact.Name]): IO[String] =
    for result <- findAndIndexMissingArtifacts(groupId, artifactNameOpt)
    yield s"Inserted $result poms"

  /** Load known refs for a group in pages to keep each DB query small. */
  private def loadKnownRefs(groupId: GroupId): IO[Set[Artifact.Reference]] =
    def loop(page: Int, acc: Set[Artifact.Reference]): IO[Set[Artifact.Reference]] =
      for
        batch <- database.getArtifactRefs(groupId, limit = artifactRefPageSize, offset = page * artifactRefPageSize)
        next = acc ++ batch
        result <-
          if batch.size == artifactRefPageSize then loop(page + 1, next)
          else IO.pure(next)
      yield result
    loop(0, Set.empty)
  end loadKnownRefs

  /** Process items in pages, with a short delay between full pages. */
  private def processPages[A](items: Seq[A], pageSize: Int)(process: Seq[A] => IO[Int]): IO[Int] =
    def loop(page: Int, total: Int): IO[Int] =
      val batch = items.slice(page * pageSize, (page + 1) * pageSize)
      if batch.isEmpty then IO.pure(total)
      else
        for
          inserted <- process(batch)
          next = total + inserted
          result <-
            if batch.size == pageSize then IO.sleep(pageDelay).flatMap(_ => loop(page + 1, next))
            else IO.pure(next)
        yield result
    end loop
    loop(0, 0)
  end processPages

  def republishArtifacts(): IO[String] =
    for
      projectStatuses <- database.getAllProjectsStatuses()
      refs = projectStatuses.collect { case (ref, status) if status.isOk || status.isUnknown || status.isFailed => ref }
      counts <- refs.mapIO(republishArtifacts)
    yield
      val successes = counts.map(_._1).sum
      val failures = counts.map(_._2).sum
      s"Re-published $successes artifacts ($failures failures)."

  private def republishArtifacts(projectRef: Project.Reference): IO[(Int, Int)] =
    for
      refs <- database.getProjectArtifactRefs(projectRef, stableOnly = false)
      publishResult <- refs.mapIO(republishArtifact(projectRef, _))
    yield
      val successes = publishResult.count(_ == PublishResult.Success)
      val failures = publishResult.size - successes
      logger.info(s"Re-published $successes artifacts of $projectRef ($failures failures)")
      (successes, failures)

  private def republishArtifact(projectRef: Project.Reference, ref: Artifact.Reference): IO[PublishResult] =
    mavenCentralClient.getPomFile(ref).toIO.flatMap {
      case Some((pomFile, creationDate)) => publishProcess.republishPom(projectRef, ref, pomFile, creationDate)
      case _ => IO.pure(PublishResult.InvalidPom)
    }
end MavenCentralService
