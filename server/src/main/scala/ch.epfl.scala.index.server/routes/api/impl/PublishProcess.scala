package ch.epfl.scala.index
package server
package routes
package api
package impl

import data._
import data.cleanup.GithubRepoExtractor
import data.download.PlayWsDownloader
import data.elastic._
import data.github._
import data.maven.{MavenModel, PomsReader, DownloadParentPoms}
import data.project.ProjectConvert

import model.misc.GithubRepo
import model.{Project, Release}
import model.release.ReleaseSelection

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.StatusCode

import com.sksamuel.elastic4s._
import ElasticDsl._

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

import java.io.{StringWriter, PrintWriter}

private[api] class PublishProcess(paths: DataPaths, dataRepository: DataRepository)(
    implicit val system: ActorSystem,
    implicit val materializer: ActorMaterializer
) extends PlayWsDownloader {

  import system.dispatcher

  /**
    * write the pom file to disk if it's a pom file (SBT will also send *.pom.sha1 and *.pom.md5)
    * - will check if there is a scm tag for github
    * - will check if the publishing user have write access to the provided repository
    *
    * Response codes:
    * Created - 201 - Data accepted and stored (by default for all files which is not *.pom)
    * NoContent - 204 - No GitHub SCM tag provided
    * Forbidden - 403 - No write access to the GitHub repository
    *
    * @param data the Publish data class holding all the data
    * @return
    */
  def writeFiles(data: PublishData): Future[(StatusCode, String)] = Future {
    if (data.isPom) {
      data.writeTemp()
      getTmpPom(data) match {
        case List(Success((pom, _, _))) =>
          getGithubRepo(pom) match {
            case None => {
              data.deleteTemp()
              (NoContent, "No Github Repo")
            }
            case Some(repo) => {
              if (data.userState.hasPublishingAuthority || data.userState.repos.contains(repo)) {
                data.writePom(paths)
                data.deleteTemp()
                updateIndex(repo, pom, data)
                (Created, "Published release")
              } else {
                data.deleteTemp()
                (Forbidden, s"${data.userState.user.login} cannot publish to ${repo.toString}")
              }
            }
          }
        case List(Failure(e)) => {
          val sw = new StringWriter()
          val pw = new PrintWriter(sw)
          e.printStackTrace(pw)

          (BadRequest, "Invalid pom: " + sw.toString())
        }
        case _ => (BadRequest, "Impossible ?")
      }
    } else {
      if (data.userState.isSonatype) ((BadRequest, "Not a POM"))
      else ((Created, "ignoring")) // for sbt, ignore SHA1, etc
    }
  }

  /**
    * Convert the POM XML data to a Maven Model
    *
    * @param data the XML String data
    * @return
    */
  private def getTmpPom(data: PublishData): List[Try[(MavenModel, LocalRepository, String)]] = {
    val path = data.tempPath.getParent

    val downloadParentPomsStep =
      new DownloadParentPoms(LocalRepository.MavenCentral, paths, Some(path))

    downloadParentPomsStep.run()

    PomsReader.tmp(paths, path).load()
  }

  /**
    * try to extract a github repository from scm tag in Maven Model
    *
    * @param pom the Maven model
    * @return
    */
  private def getGithubRepo(pom: MavenModel): Option[GithubRepo] =
    (new GithubRepoExtractor(paths)).apply(pom)

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
  private def updateIndex(repo: GithubRepo, pom: MavenModel, data: PublishData): Future[Unit] = {
    println("updating " + pom.artifactId)

    new GithubDownload(paths, Some(data.credentials))
      .run(repo, data.downloadInfo, data.downloadReadme, data.downloadContributors)

    val githubRepoExtractor = new GithubRepoExtractor(paths)
    val Some(GithubRepo(organization, repository)) = githubRepoExtractor(pom)
    val projectReference = Project.Reference(organization, repository)

    def updateProjectReleases(project: Option[Project], releases: List[Release]): Future[Unit] = {

      val repository =
        if (data.userState.hasPublishingAuthority) LocalRepository.MavenCentral
        else LocalRepository.UserProvided

      Meta.append(paths, Meta(data.hash, data.path, data.created), repository)

      val converter = new ProjectConvert(paths)

      val (newProject, newReleases) = converter(
        pomsRepoSha = List((pom, repository, data.hash)),
        cachedReleases = cachedReleases
      ).head

      cachedReleases = upserts(cachedReleases, projectReference, newReleases)

      val updatedProject = newProject.copy(
        keywords = data.keywords,
        liveData = true
      )

      val projectUpdate =
        project match {
          case Some(project) => {

            esClient
              .execute(
                update(project.id.get).in(indexName / projectsCollection).doc(updatedProject)
              )
              .map(_ => println("updating project " + pom.artifactId))
          }

          case None =>
            esClient
              .execute(
                index.into(indexName / projectsCollection).source(updatedProject)
              )
              .map(_ => println("inserting project " + pom.artifactId))
        }

      val releaseUpdate =
        if (!releases.exists(r => r.reference == newReleases.head.reference)) {
          // create new release
          esClient
            .execute(
              index
                .into(indexName / releasesCollection)
                .source(
                  newReleases.head.copy(liveData = true)
                ))
            .map(_ => ())
        } else { Future.successful(()) }

      for {
        _ <- projectUpdate
        _ <- releaseUpdate
      } yield ()
    }

    for {
      project <- dataRepository.project(projectReference)
      releases <- dataRepository.releases(projectReference, ReleaseSelection.empty)
      _ <- updateProjectReleases(project, releases)
    } yield ()
  }

  private var cachedReleases = Map.empty[Project.Reference, Set[Release]]
}
