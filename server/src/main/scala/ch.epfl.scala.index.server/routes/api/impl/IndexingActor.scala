package ch.epfl.scala.index
package server
package routes
package api
package impl

import akka.actor.{Actor, ActorSystem}
import akka.stream.ActorMaterializer
import ch.epfl.scala.index.data.{DataPaths, LocalPomRepository, Meta, upserts}
import ch.epfl.scala.index.data.cleanup.GithubRepoExtractor
import ch.epfl.scala.index.data.elastic.{esClient, indexName}
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.ReleaseModel
import ch.epfl.scala.index.data.project.ProjectConvert
import ch.epfl.scala.index.model.{Project, Release}
import ch.epfl.scala.index.model.misc.GithubRepo
import com.sksamuel.elastic4s.ElasticDsl._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import ch.epfl.scala.index.data.elastic._
import org.slf4j.LoggerFactory

class IndexingActor(
    paths: DataPaths,
    dataRepository: DataRepository,
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer
) extends Actor {
  private val log = LoggerFactory.getLogger(getClass)
  import system.dispatcher

  def receive = {
    case updateIndexData: UpdateIndex => {
      sender ! Await.result(updateIndex(
                              updateIndexData.repo,
                              updateIndexData.pom,
                              updateIndexData.data,
                              updateIndexData.localRepo
                            ),
                            1.minute)
    }
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
  private def updateIndex(repo: GithubRepo,
                          pom: ReleaseModel,
                          data: PublishData,
                          localRepository: LocalPomRepository): Future[Unit] = {

    println("updating " + pom.artifactId)

    val githubDownload = new GithubDownload(paths, Some(data.credentials))
    githubDownload.run(repo,
                       data.downloadInfo,
                       data.downloadReadme,
                       data.downloadContributors)

    val githubRepoExtractor = new GithubRepoExtractor(paths)
    val Some(GithubRepo(organization, repository)) = githubRepoExtractor(pom)
    val projectReference = Project.Reference(organization, repository)

    def updateProjectReleases(project: Option[Project],
                              releases: List[Release]): Future[Unit] = {

      val converter = new ProjectConvert(paths, githubDownload)

      val (newProject, newReleases) = converter(
        pomsRepoSha = List((pom, localRepository, data.hash)),
        cachedReleases = cachedReleases
      ).head

      cachedReleases = upserts(cachedReleases, projectReference, newReleases)

      val updatedProject = newProject.copy(
        liveData = true
      )

      val projectUpdate =
        project match {
          case Some(project) => {

            esClient
              .execute(
                update(project.id.get)
                  .in(indexName / projectsCollection)
                  .doc(updatedProject)
              )
              .map(_ => log.info("updating project " + pom.artifactId))
          }

          case None =>
            esClient
              .execute(
                indexInto(indexName / projectsCollection)
                  .source(updatedProject)
              )
              .map(_ => log.info("inserting project " + pom.artifactId))
        }

      val releaseUpdate =
        if (!releases.exists(r => r.reference == newReleases.head.reference)) {
          // create new release
          esClient
            .execute(
              indexInto(indexName / releasesCollection)
                .source(
                  newReleases.head.copy(liveData = true)
                )
            )
            .map(_ => ())
        } else { Future.successful(()) }

      for {
        _ <- projectUpdate
        _ <- releaseUpdate
      } yield ()
    }

    for {
      project <- dataRepository.project(projectReference)
      releases <- dataRepository.releases(projectReference)
      _ <- updateProjectReleases(project, releases)
    } yield ()
  }

  private var cachedReleases = Map.empty[Project.Reference, Set[Release]]
}

case class UpdateIndex(
    repo: GithubRepo,
    pom: ReleaseModel,
    data: PublishData,
    localRepo: LocalPomRepository
)
