package scaladex.server.route.api
package impl

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.ActorSystem
import org.slf4j.LoggerFactory
import scaladex.core.model.Project
import scaladex.core.service.WebDatabase
import scaladex.data.maven.ArtifactModel
import scaladex.data.meta.ArtifactConverter
import scaladex.infra.storage.DataPaths
import scaladex.infra.storage.LocalPomRepository

class IndexingActor(
    paths: DataPaths,
    database: WebDatabase,
    implicit val system: ActorSystem
) extends Actor {
  private val log = LoggerFactory.getLogger(getClass)
  private val artifactConverter = new ArtifactConverter(paths)

  def receive: PartialFunction[Any, Unit] = {
    case updateIndexData: UpdateIndex =>
      // TODO be non-blocking
      sender() ! Await.result(
        insertArtifact(
          updateIndexData.repo,
          updateIndexData.pom,
          updateIndexData.data,
          updateIndexData.localRepo
        ),
        1.minute
      )
  }

  /**
   * Main task to update the scaladex index.
   * - download GitHub info if allowd
   * - download GitHub contributors if allowed
   * - download GitHub readme if allowed
   * - search for project and
   *   1. update project
   *      1. Search for an artifact
   *      2. update or create new artifact
   *   2. create new project
   *
   * @param repo the Github repo reference model
   * @param pom the Maven Model
   * @param data the main publish data
   * @return
   */
  private def insertArtifact(
      projectRef: Project.Reference,
      pom: ArtifactModel,
      data: PublishData,
      localRepository: LocalPomRepository
  ): Future[Unit] = {

    log.debug("updating " + pom.artifactId)

    val artifact = artifactConverter.convert(
      pom,
      projectRef,
      data.hash,
      Some(data.created)
    )
    artifact
      .map { case (artifact, deps) => database.insertArtifact(artifact, deps, Instant.now) }
      .getOrElse(
        Future.successful(log.info(s"${pom.mavenRef.name} is not inserted"))
      )
  }
}

case class UpdateIndex(repo: Project.Reference, pom: ArtifactModel, data: PublishData, localRepo: LocalPomRepository)
