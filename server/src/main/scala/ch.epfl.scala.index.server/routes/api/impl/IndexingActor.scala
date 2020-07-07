package ch.epfl.scala.index
package server
package routes
package api
package impl

import akka.actor.{Actor, ActorSystem}
import akka.stream.ActorMaterializer
import ch.epfl.scala.index.data.cleanup.GithubRepoExtractor
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.ReleaseModel
import ch.epfl.scala.index.data.project.ProjectConvert
import ch.epfl.scala.index.data.{DataPaths, LocalPomRepository}
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.model.{Project, Release}
import ch.epfl.scala.index.search.DataRepository
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class IndexingActor(
    paths: DataPaths,
    dataRepository: DataRepository,
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer
) extends Actor {
  private val log = LoggerFactory.getLogger(getClass)
  import system.dispatcher

  def receive = {
    case updateIndexData: UpdateIndex =>
      // TODO be non-blocking
      sender ! Await.result(updateIndex(
                              updateIndexData.repo,
                              updateIndexData.pom,
                              updateIndexData.data,
                              updateIndexData.localRepo
                            ),
                            1.minute)
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
    githubDownload.run(repo,
                       data.downloadInfo,
                       data.downloadReadme,
                       data.downloadContributors)

    val githubRepoExtractor = new GithubRepoExtractor(paths)
    val Some(GithubRepo(organization, repository)) = githubRepoExtractor(pom)
    val projectReference = Project.Reference(organization, repository)

    def updateProjectReleases(project: Option[Project],
                              releases: Seq[Release]): Future[Unit] = {

      val converter = new ProjectConvert(paths, githubDownload)

      val (newProject, newReleases, dependencies) = converter
        .convertAll(
          List((pom, localRepository, data.hash)),
          project.map(p => p.reference -> releases).toMap
        )
        .next

      val projectUpdate = project match {
        case Some(project) =>
          dataRepository
            .updateProject(newProject.copy(id = project.id, liveData = true))
            .map(_ => log.info("updating project " + pom.artifactId))

        case None =>
          dataRepository
            .insertProject(newProject.copy(liveData = true))
            .map(_ => log.info("inserting project " + pom.artifactId))
      }

      val releaseUpdate = newReleases.headOption match {
        case Some(release)
            if !releases.exists(r => r.reference == release.reference) =>
          log.info(s"inserting release ${release.maven}")
          for {
            _ <- dataRepository.insertRelease(release.copy(liveData = true))
            response <- dataRepository.insertDependencies(dependencies)
          } yield {
            if (response.hasFailures) {
              response.failures.foreach(f => log.error(f.failureMessage))
              log.error(
                s"failed inserting the ${dependencies.size} dependencies of ${release.maven}"
              )
            } else {
              log.error(
                s"Inserted ${dependencies.size} dependencies of ${release.maven}"
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

    for {
      project <- dataRepository.getProject(projectReference)
      releases <- dataRepository.getProjectReleases(projectReference)
      _ <- updateProjectReleases(project, releases)
    } yield ()
  }
}

case class UpdateIndex(
    repo: GithubRepo,
    pom: ReleaseModel,
    data: PublishData,
    localRepo: LocalPomRepository
)
