package ch.epfl.scala.index
package server

import data.bintray._
import data.cleanup.GithubRepoExtractor
import data.download.PlayWsDownloader
import data.elastic._
import data.github.{GithubCredentials, GithubDownload}
import data.maven.PomsReader
import data.project.ProjectConvert

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import org.joda.time.DateTime

import play.api.libs.ws.WSAuthScheme

import com.sksamuel.elastic4s._
import ElasticDsl._

import scala.concurrent.{Await, Future}

class PublishProcess(
  implicit val system: ActorSystem,
  implicit val materializer: ActorMaterializer,
  implicit val api: Api
) extends PlayWsDownloader {

  import system.dispatcher

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

  /**
   * verify the sha1 file hash
   *
   * @param path the path to the file to verify
   * @param sha1 the sha1 hash
   * @return
   */
  private def verifySHA1FileHash(path: Path, sha1: String): Boolean = {

    val source = scala.io.Source.fromFile(path.toFile)
    val content = source.mkString
    source.close()

    verifyChecksum(content, sha1)
  }
  /**
   * will compute ans sha1 hash from given string and verify that against
   * given compare hash
   *
   * @param toVerify the string to compute
   * @param sha1 the verify hash
   * @return
   */
  def verifyChecksum(toVerify: String, sha1: String) = {

    computeSha1(toVerify) == sha1
  }

  def writeFiles(fileName: String, data: String, credentials: GithubCredentials) = Future {


    val tmpName = fileName.replace(".sha1", "").replace(".pom", "")
    //println(s"Receive: $tmpName")
    val hash = computeSha1(tmpName)
    val path = tmpPath(hash)

    if (fileName matches """.*\.pom.sha1""") {

      if (verifySHA1FileHash(path, data)) {

        val pomfilePath = pomPath(data)
//        println("valid sha1 - move file to pom directory")

        if (!Files.exists(pomfilePath)) {

          Files.move(path, pomfilePath)
        }

        extractPomData(pomfilePath, data, credentials)

      } else {

        println("invalid sha1 - delete file")
        Files.delete(path)
      }
    } else if (fileName matches """.*\.pom""") {

      if (Files.exists(path)) {

        Files.delete(path)
      }
//      println("write file to disk")
      Files.write(path, data.getBytes(StandardCharsets.UTF_8))
    } else {

//      println("ignored ...")
    }
  }

  def extractPomData(path: Path, sha1: String, githubCredentials: GithubCredentials) = {

    val pom = PomsReader.load(path)
    val githubRepoExtractor = new GithubRepoExtractor
    val githubRepo = githubRepoExtractor(pom)

    githubRepo.map {repo =>

      new GithubDownload(Some(githubCredentials), system, materializer).run(repo)

      val bintray = BintraySearch(
        sha1,
        None,
        s"${pom.groupId}:${pom.artifactId}",
        pom.artifactId,
        "",
        0,
        pom.version,
        pom.groupId,
        pom.artifactId,
        new DateTime()
      )

      val (newProject, newReleases) = ProjectConvert(List((pom, List(bintray)))).head

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
              esClient.execute(update(id) in(indexName / projectsCollection) doc newProject)
            }
          case None =>

            println("index new project")
            esClient.execute(index.into(indexName / projectsCollection).source(newProject))
        }


        releases.foreach{rel => println(s"Release ${rel.reference.version}")}
        /* there can be only one release */
        if (!releases.exists(r => r.reference == newReleases.head.reference)) {

          println("add new release")
          esClient.execute(index.into(indexName / releasesCollection).source(newReleases.head))
        }
      }

      pom
    }
  }

  def authenticate(githubCredentials: GithubCredentials): Boolean = {

    import scala.concurrent.duration._
    val req = wsClient.url("https://api.github.com/user").withAuth(githubCredentials.username, githubCredentials.password, WSAuthScheme.BASIC)
    val response = Await.result(req.get, 5.seconds)

    200 == response.status
  }
}
