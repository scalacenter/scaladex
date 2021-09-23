package ch.epfl.scala.index
package server
package routes
package api
package impl

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.ActorSystem
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.ReleaseModel
import ch.epfl.scala.index.data.project.ProjectConvert
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.services.DatabaseApi
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.LocalPomRepository
import org.slf4j.LoggerFactory

class IndexingActor(
    paths: DataPaths,
    dataRepository: ESRepo,
    db: DatabaseApi,
    implicit val system: ActorSystem
) extends Actor {
  private val log = LoggerFactory.getLogger(getClass)
  import system.dispatcher

  def receive: PartialFunction[Any, Unit] = {
    case updateIndexData: UpdateIndex =>
      // TODO be non-blocking
      sender() ! Await.result(
        updateIndex(
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
   *      1. Search for release
   *      2. update or create new release
   *   2. create new project
   *
   * @param repo the Github repo reference model
   * @param pom the Maven Model
   * @param data the main publish data
   * @return
   */
  private def updateIndex(
      repo: GithubRepo,
      pom: ReleaseModel,
      data: PublishData,
      localRepository: LocalPomRepository
  ): Future[Unit] = {

    log.debug("updating " + pom.artifactId)

    val githubDownload = new GithubDownload(paths, Some(data.credentials))
    githubDownload.run(
      repo,
      data.downloadInfo,
      data.downloadReadme,
      data.downloadContributors
    )

    val projectReference = Project.Reference(repo.organization, repo.repository)

    for {
      project <- db.findProject(projectReference)
      converter = new ProjectConvert(paths, githubDownload)
      converted = converter.convertOne(
        pom,
        localRepository,
        data.hash,
        data.created,
        repo,
        project
      )
      _ = converted
        .map { case (p, r, d) =>
          for {
            _ <- db.insertOrUpdateProject(p)
            _ <- db.insertReleases(
              Seq(r)
            ) // todo: filter already existing releases , to only update them
            _ <- db.insertDependencies(
              d
            ) // todo: filter already existing dependencies , to only update them
            _ = log.info(s"${pom.mavenRef.name} has been inserted")
          } yield ()
        }
        .getOrElse(
          Future.successful(log.info(s"${pom.mavenRef.name} is not inserted"))
        )
    } yield ()
  }
}

case class UpdateIndex(
    repo: GithubRepo,
    pom: ReleaseModel,
    data: PublishData,
    localRepo: LocalPomRepository
)
