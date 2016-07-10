package ch.epfl.scala.index
package server

import data.bintray._
import data.cleanup.GithubRepoExtractor
import data.download.PlayWsDownloader
import data.elastic._
import ch.epfl.scala.index.data.github._
import data.maven.{MavenModel, PomsReader}
import data.project.ProjectConvert
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.StatusCodes._
import org.joda.time.DateTime
import play.api.libs.ws.WSAuthScheme
import com.sksamuel.elastic4s._
import ElasticDsl._
import akka.http.scaladsl.model.StatusCode
import ch.epfl.scala.index.model.misc.GithubRepo

import scala.concurrent.{Await, Future}

class PublishProcess(
  implicit val system: ActorSystem,
  implicit val materializer: ActorMaterializer,
  implicit val api: Api
) extends PlayWsDownloader {

  import system.dispatcher

  def writeFiles(data: PublishData): Future[StatusCode] = Future {

    if (data.isPom) {

      data.writeTemp()
      val pom = getPom(data)
      val repos = getGithubRepo(pom)

      if (1 < repos.size) {

        data.deleteTemp()
        MultipleChoices
      } else {

        val repo = repos.head

        if (hasWriteAccess(data.credentials, repo)) {

          data.writePom()
          data.deleteTemp()
          updateIndex(repo, pom, data)

          Created
        } else {

          Forbidden
        }
      }
    } else {

      /* ignore the file at this case */
      Created
    }
  }

  def getPom(data: PublishData): MavenModel = PomsReader.load(data.tempPath)
  def getGithubRepo(pom: MavenModel): Set[GithubRepo] = (new GithubRepoExtractor).apply(pom)

  def updateIndex(repo: GithubRepo, pom: MavenModel, data: PublishData) = Future {

    new GithubDownload(Some(data.credentials), system, materializer).run(repo, data.downloadInfo, data.downloadReadme, data.downloadContributors)

    val bintray = BintraySearch(data.hash, None, s"${pom.groupId}:${pom.artifactId}", pom.artifactId, "", 0, pom.version, pom.groupId, pom.artifactId, new DateTime())

    val (newProject, newReleases) = ProjectConvert(List((pom, List(bintray)))).head

    val updatedProject = newProject.copy(keywords = data.keywords)
    val projectSearch = api.projectPage(newProject.reference)
    val releaseSearch = api.releases(newProject.reference)

    for {
      projectResult <- projectSearch
      releases <- releaseSearch
    } yield {

      projectResult match {

        case Some((project, _, versions, release, _)) =>

          println(project._id)
          project._id.map { id =>

            println("update project with new reference")
            esClient.execute(update(id) in (indexName / projectsCollection) doc updatedProject)
          }
        case None =>

          println("index new project")
          esClient.execute(index.into(indexName / projectsCollection).source(updatedProject))
      }


      releases.foreach { rel => println(s"Release ${rel.reference.version}") }
      /* there can be only one release */
      if (!releases.exists(r => r.reference == newReleases.head.reference)) {

        println("add new release")
        esClient.execute(index.into(indexName / releasesCollection).source(newReleases.head))
      } else {

        for {
          release <- releases.find(r => r.reference == newReleases.head.reference)
          id <- release._id
        } yield {

          esClient.execute(update(id).in(indexName / releasesCollection) doc newReleases.head)
        }
      }
    }
  }

  def authenticate(githubCredentials: GithubCredentials): Boolean = {

    import scala.concurrent.duration._
    val req = wsClient.url("https://api.github.com/user").withAuth(githubCredentials.username, githubCredentials.password, WSAuthScheme.BASIC)
    val response = Await.result(req.get, 5.seconds)

    200 == response.status
  }

  def hasWriteAccess(githubCredentials: GithubCredentials, repository: GithubRepo): Boolean = {

    import scala.concurrent.duration._
    import ch.epfl.scala.index.data.github.Json4s._
    import org.json4s.native.Serialization.read

    val req = wsClient
      .url(s"https://api.github.com/repos/${repository.organization}/${repository.repo}")
      .withAuth(githubCredentials.username, githubCredentials.password, WSAuthScheme.BASIC)
      .withHeaders("Accept" -> "application/vnd.github.v3+json")
    val response = Await.result(req.get, 5.seconds)

    val githubRepo = read[Repository](response.body)

    githubRepo.permissions.exists(_.push)
  }
}

case class PublishData(
  path: String,
  data: String,
  credentials: GithubCredentials,
  downloadInfo: Boolean,
  downloadContributors: Boolean,
  downloadReadme: Boolean,
  keywords: List[String]
) {

  lazy val isPom: Boolean = path matches """.*\.pom"""
  lazy val hash = computeSha1(data)
  lazy val tempPath = tmpPath(hash)
  lazy val savePath = pomPath(hash)

  private def write(writePath: Path): Unit = {

    delete(writePath)
    Files.write(writePath, data.getBytes(StandardCharsets.UTF_8))
    ()
  }

  private def delete(deletePath: Path): Unit = {

    if (Files.exists(deletePath)) {

      Files.delete(deletePath)
    }
    ()
  }

  def writeTemp() = write(tempPath)
  def writePom() = write(savePath)

  def deleteTemp() = delete(tempPath)

  /**
   * resolve the filename for a specific pom by sha1
   *
   * @param sha1 the sha1 hash of the file
   * @return
   */
  private def pomPath(sha1: String) = bintrayPomBase.resolve(s"$sha1.pom")

  private def tmpPath(sha1: String) = tmpBase.resolve(s"$sha1.pom")

  private def computeSha1(data: String): String = {

    val md = java.security.MessageDigest.getInstance("SHA-1")
    md.digest(data.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

}
