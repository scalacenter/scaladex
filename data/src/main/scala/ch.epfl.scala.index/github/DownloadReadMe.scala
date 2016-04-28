package ch.epfl.scala.index
package github

import maven._
import cleanup._

import me.tongfei.progressbar._

import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.headers._

import akka.stream.scaladsl._
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.Await

import java.util.Base64
import java.nio.charset.StandardCharsets
import java.nio.file._

class DownloadReadMe(implicit system: ActorSystem, materializer: ActorMaterializer) extends DefaultJsonProtocol {
  import system.dispatcher

  private case class ReadMe(content: String, encoding: String, path: String)
  private implicit val readmeFormat = jsonFormat3(ReadMe)

  private val githubHttpFlow = Http().cachedHostConnectionPoolHttps[GithubRepo]("api.github.com")
  private val githubRepos = {
    val poms = Poms.get.collect{ case Success(p) => PomConvert(p) }
    val scmCleanup = new ScmCleanup
    val repos = poms.flatMap(p => scmCleanup.find(p).toList).toSet
    repos
  }
  private val progress = new ProgressBar("Downloading READMEs", githubRepos.size)
  progress.start()

  private val token = {
    val home = System.getProperty("user.home")
    val path = home + "/.github/.credentials"
    val source = io.Source.fromFile(path)
    val t = source.mkString
    source.close()
    t
  }

  private def request(gh: GithubRepo): (HttpRequest, GithubRepo) = {
    val GithubRepo(user, repo) = gh
    val request =
      HttpRequest(
        uri = Uri(s"https://api.github.com/repos/$user/$repo/readme")
      ).withHeaders(Authorization(GenericHttpCredentials("token", token)))
    (request, gh)
  }

  private val downloadReadme = Source(githubRepos)
    .map(request)
    .via(githubHttpFlow)
    .mapAsync(1){
      case (Success(response), repo) => {
        println(response)
        Unmarshal(response).to[ReadMe].map(body =>
          if(response.status == StatusCodes.OK) Right((body, repo))
          else Left((body, repo))
        )
      }
      case (Failure(e), _) => Future.failed(e)
    }.map{
      case Right((ReadMe(content, encoding, path), GithubRepo(user, repo))) => {
        if(encoding == "base64") {
          progress.step()
          val readmePath = githubIndexBase.resolve(Paths.get(user, repo, path))
          Files.createDirectories(readmePath.getParent());
          Files.write(
            readmePath,
            (new String(Base64.getDecoder.decode(content), "UTF-8")).getBytes("UTF-8"),
            StandardOpenOption.CREATE, 
            StandardOpenOption.TRUNCATE_EXISTING
          )
        } else {
          println(s"$user/$repo readme encoding is not supported: $encoding")
        }

      }
      case Left(e) => println(e)
    }

  def run() = {
    Await.result(downloadReadme.runForeach(_ => ()), Duration.Inf)
  }
}