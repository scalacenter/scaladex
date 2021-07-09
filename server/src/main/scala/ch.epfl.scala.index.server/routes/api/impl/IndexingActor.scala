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
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.LocalPomRepository
import ch.epfl.scala.index.data.cleanup.GithubRepoExtractor
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.ReleaseModel
import ch.epfl.scala.index.data.project.ProjectConvert
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.model.release.ScalaDependency
import ch.epfl.scala.index.search.ESRepo
import org.slf4j.LoggerFactory

class IndexingActor(
    paths: DataPaths,
    dataRepository: ESRepo,
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

    val githubRepoExtractor = new GithubRepoExtractor(paths)
    val Some(GithubRepo(organization, repository)) = githubRepoExtractor(pom)
    val projectReference = Project.Reference(organization, repository)

    def updateProjectReleases(
        project: Option[Project],
        releases: Seq[Release]
    ): Future[Unit] = {

      val converter = new ProjectConvert(paths, githubDownload)

      converter
        .convertAll(
          List((pom, localRepository, data.hash)),
          project.map(p => p.reference -> releases).toMap
        )
        .nextOption() match {
        case Some((newProject, newReleases, dependencies)) =>
          createOrUpdateProjectReleases(
            project,
            newProject,
            releases,
            newReleases,
            dependencies
          )
        case None =>
          Future.successful(
            log.info(s"${pom.artifactId} is not a valid Scala artifact")
          )
      }
    }

    for {
      project <- dataRepository.getProject(projectReference)
      releases <- dataRepository.getProjectReleases(projectReference)
      _ <- updateProjectReleases(project, releases)
    } yield ()
  }

  def createOrUpdateProjectReleases(
      project: Option[Project],
      newProject: Project,
      releases: Seq[Release],
      newReleases: Seq[Release],
      dependencies: Seq[ScalaDependency]
  ): Future[Unit] = {
    val projectUpdate = project match {
      case Some(project) =>
        dataRepository
          .updateProject(newProject.copy(id = project.id, liveData = true))
          .map(_ => log.info(s"Updating project ${project.githubRepo}"))

      case None =>
        dataRepository
          .insertProject(newProject.copy(liveData = true))
          .map(_ => log.info(s"Creating new project ${newProject.githubRepo}"))
    }

    val releaseUpdate = newReleases.headOption match {
      case Some(release)
          if !releases.exists(_.reference == release.reference) =>
        log.info(s"Adding release ${release.maven}")
        for {
          _ <- dataRepository.insertRelease(release.copy(liveData = true))
          items <- dataRepository.insertDependencies(dependencies)
        } yield {
          val failures = items.filter(_.status > 300)
          if (failures.nonEmpty) {
            failures.foreach(
              _.error.foreach(error => log.error(error.reason))
            )
            log.error(
              s"Failed adding the ${dependencies.size} dependencies of ${release.maven}"
            )
          } else {
            log.error(
              s"Added ${dependencies.size} dependencies of ${release.maven}"
            )
          }
        }

      case _ => Future.successful(())
    }

    for {
      _ <- projectUpdate
      _ <- releaseUpdate
    } yield ()
  }
}

case class UpdateIndex(
    repo: GithubRepo,
    pom: ReleaseModel,
    data: PublishData,
    localRepo: LocalPomRepository
)
