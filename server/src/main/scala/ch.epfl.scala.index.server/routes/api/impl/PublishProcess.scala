package ch.epfl.scala.index
package server
package routes
package api
package impl

import data.bintray._
import data.cleanup.GithubRepoExtractor
import data.download.PlayWsDownloader
import data.elastic._
import data.github._
import data.maven.{MavenModel, PomsReader}
import data.project.ProjectConvert

import model.misc.GithubRepo
import model.{Project, Release}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.StatusCode

import com.sksamuel.elastic4s._
import ElasticDsl._

import org.joda.time.DateTime

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.concurrent.Future

private[api] class PublishProcess(dataRepository: DataRepository)(
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
  def writeFiles(data: PublishData): Future[StatusCode] = Future {
    if (data.isPom) {
      data.writeTemp()
      val pom = getPom(data)
      getGithubRepo(pom) match {
        case None => {
          data.deleteTemp()
          NoContent
        }
        case Some(repo) => {
          // if(data.userState.isSonatype || data.userState.repos.contains(repo)) {
            data.writePom()
            data.deleteTemp()
            updateIndex(repo, pom, data)
            Created
          // } else {
          //   data.deleteTemp()
          //   Forbidden
          // }
        }
      }
    } else {
      Created /* ignore the file at this case */
    }
  }

  /**
    * Convert the POM XML data to a Maven Model
    *
    * @param data the XML String data
    * @return
    */
  private def getPom(data: PublishData): MavenModel =
    PomsReader.load(data.tempPath)

  /**
    * try to extract a github repository from scm tag in Maven Model
    *
    * @param pom the Maven model
    * @return
    */
  private def getGithubRepo(pom: MavenModel): Option[GithubRepo] =
    (new GithubRepoExtractor).apply(pom)

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

    // new GithubDownload(Some(data.credentials))
    //   .run(repo, data.downloadInfo, data.downloadReadme, data.downloadContributors)

    def updateProjectReleases(project: Option[Project], releases: List[Release]): Future[Unit] = {

      val bintray = BintraySearch(
        created = new DateTime(),
        `package` = s"${pom.groupId}:${pom.artifactId}",
        owner = "bintray",
        repo = "jcenter",
        sha1 = data.hash,
        sha256 = None,
        name = pom.artifactId,
        path = pom.groupId.replaceAllLiterally(".", "/") + "/" + pom.artifactId,
        size = 0,
        version = pom.version
      )

      val (newProject, newReleases) = ProjectConvert(List((pom, List(bintray))), existingProject = project, existingReleases = releases).head

      val updatedProject = newProject.copy(keywords = data.keywords, liveData = true, test = data.test)

      val projectUpdate =
        project match {
          case Some(project) => {

            println(updatedProject.artifacts)

            esClient.execute(update(project.id.get).in(indexName / projectsCollection).doc(updatedProject))
                    .map(_ => println("updating project " + pom.artifactId))
          }


          case None =>
            esClient.execute(index.into(indexName / projectsCollection).source(updatedProject))
                    .map(_ => println("inserting project " + pom.artifactId))
        }

      val releaseUpdate = 
        if (!releases.exists(r => r.reference == newReleases.head.reference)) {
          // create new release
          esClient.execute(index.into(indexName / releasesCollection).source(
            newReleases.head.copy(liveData = true, test = data.test)
          )).map(_ => ())
        } else { Future.successful(())} 


      for {
        _ <- projectUpdate
        _ <- releaseUpdate
      } yield ()
    }

    val githubRepoExtractor = new GithubRepoExtractor()
    val Some(GithubRepo(organization, repository)) = githubRepoExtractor(pom)
    val projectReference = Project.Reference(organization, repository)

    for {
      project  <- dataRepository.project(projectReference)
      releases <- dataRepository.releases(projectReference, None)
      _        <- updateProjectReleases(project, releases)
    } yield ()
  }
}

/**
  * Publish data model / Settings
  * @param path the file name send to scaladex
  * @param data the file content
  * @param credentials the credentials (username & password)
  * @param downloadInfo flag for downloading info
  * @param downloadContributors flag for downloading contributors
  * @param downloadReadme flag for downloading the readme file
  * @param keywords the keywords for the project
  * @param test to try the api
  */
private[api] case class PublishData(
    path: String,
    data: String,
    // credentials: GithubCredentials,
    // userState: UserState,
    downloadInfo: Boolean,
    downloadContributors: Boolean,
    downloadReadme: Boolean,
    keywords: Set[String],
    test: Boolean
) {

  lazy val isPom: Boolean = path matches """.*\.pom"""
  lazy val hash           = computeSha1(data)
  lazy val tempPath       = tmpPath(hash)
  lazy val savePath       = pomPath(hash)

  /**
    * write the file content to given path
    * @param destination the given destination
    */
  private def write(destination: Path): Unit = {

    delete(destination)
    Files.write(destination, data.getBytes(StandardCharsets.UTF_8))
    ()
  }

  /**
    * delete a given file
    * @param file the file name to delete
    */
  private def delete(file: Path): Unit = {

    if (Files.exists(file)) Files.delete(file)
    ()
  }

  /**
    * write the temp file to index/bintray/tmp
    */
  def writeTemp() = write(tempPath)

  /**
    * write the pom file to /index/bintray/pom_sha1
    */
  def writePom() = write(savePath)

  /**
    * delete the temp add file
    */
  def deleteTemp() = delete(tempPath)

  /**
    * resolve the filename for a specific pom by sha1
    *
    * @param sha1 the sha1 hash of the file
    * @return
    */
  private def pomPath(sha1: String) = bintrayPomBase.resolve(s"$sha1.pom")

  /**
    * get the tmp save path for the pom file
    * @param sha1 the sha1 hash
    * @return
    */
  private def tmpPath(sha1: String) = File.createTempFile(sha1, ".pom").toPath

  /**
    * generate SHA1 hash from a given String
    * @param data the sha1 hash
    * @return
    */
  private def computeSha1(data: String): String = {

    val md = java.security.MessageDigest.getInstance("SHA-1")
    md.digest(data.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

}
