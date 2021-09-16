package ch.epfl.scala.index
package server
package routes
package api
package impl

import java.io.PrintWriter
import java.io.StringWriter

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import ch.epfl.scala.index.data._
import ch.epfl.scala.index.data.cleanup.GithubRepoExtractor
import ch.epfl.scala.index.data.download.PlayWsDownloader
import ch.epfl.scala.index.data.maven.DownloadParentPoms
import ch.epfl.scala.index.data.maven.PomsReader
import ch.epfl.scala.index.data.maven.ReleaseModel
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.LocalPomRepository
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.search.ESRepo
import ch.epfl.scala.services.DatabaseApi
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.LocalPomRepository
import org.slf4j.LoggerFactory

private[api] class PublishProcess(
    paths: DataPaths,
    dataRepository: ESRepo,
    db: DatabaseApi
)(implicit
    val system: ActorSystem
) extends PlayWsDownloader {

  import system.dispatcher
  private val log = LoggerFactory.getLogger(getClass)
  private val indexingActor = system.actorOf(
    Props(classOf[impl.IndexingActor], paths, dataRepository, db, system)
  )

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
  def writeFiles(data: PublishData): Future[(StatusCode, String)] = {
    if (data.isPom) {
      log.info("Publishing a POM")
      Future {
        data.writeTemp()
      }.flatMap { _ =>
        getTmpPom(data) match {
          case List(Success((pom, _, _))) =>
            getGithubRepo(pom) match {
              case None => {
                log.warn("POM saved without Github information")
                data.deleteTemp()
                Future.successful((NoContent, "No Github Repo"))
              }
              case Some(repo) => {
                if (
                  data.userState.hasPublishingAuthority || data.userState.repos
                    .contains(repo)
                ) {
                  data.writePom(paths)
                  data.deleteTemp()

                  val repository =
                    if (data.userState.hasPublishingAuthority)
                      LocalPomRepository.MavenCentral
                    else LocalPomRepository.UserProvided

                  Meta.append(
                    paths,
                    Meta(data.hash, data.path, data.created),
                    repository
                  )

                  log.info(
                    s"Saved ${pom.groupId}:${pom.artifactId}:${pom.version}"
                  )

                  indexingActor ! UpdateIndex(repo, pom, data, repository)

                  Future.successful((Created, "Published release"))
                } else {
                  log.warn(
                    s"User ${data.userState.info.login} attempted to publish to ${repo.toString}"
                  )
                  data.deleteTemp()
                  Future.successful(
                    (
                      Forbidden,
                      s"${data.userState.info.login} cannot publish to ${repo.toString}"
                    )
                  )
                }
              }
            }
          case List(Failure(e)) => {
            log.error("Invalid POM " + e)
            val sw = new StringWriter()
            val pw = new PrintWriter(sw)
            e.printStackTrace(pw)

            Future.successful((BadRequest, "Invalid pom: " + sw.toString()))
          }
          case _ =>
            log.error("Unable to write POM data")
            Future.successful((BadRequest, "Impossible ?"))
        }
      }
    } else {
      if (data.userState.isSonatype)
        Future.successful((BadRequest, "Not a POM"))
      else
        Future.successful((Created, "ignoring")) // for sbt, ignore SHA1, etc
    }
  }

  /**
   * Convert the POM XML data to a Maven Model
   *
   * @param data the XML String data
   * @return
   */
  private def getTmpPom(
      data: PublishData
  ): List[Try[(ReleaseModel, LocalPomRepository, String)]] = {
    val path = data.tempPath.getParent

    val downloadParentPomsStep =
      new DownloadParentPoms(LocalPomRepository.MavenCentral, paths, Some(path))

    downloadParentPomsStep.run()

    PomsReader.tmp(paths, path).load()
  }

  /**
   * try to extract a github repository from scm tag in Maven Model
   *
   * @param pom the Maven model
   * @return
   */
  private def getGithubRepo(pom: ReleaseModel): Option[GithubRepo] =
    (new GithubRepoExtractor(paths)).apply(pom)

  private var cachedReleases = Map.empty[Project.Reference, Set[Release]]
}
