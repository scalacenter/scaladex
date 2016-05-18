package ch.epfl.scala.index
package data
package github

import maven._
import cleanup._

import me.tongfei.progressbar._

import spray.json._

import akka.stream.scaladsl._
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem

import scala.concurrent.duration._
import scala.concurrent.Await

import scala.util.Success

import java.util.Base64
import java.nio.file._

/*
 We might implement this later
 */
class DownloadReadMe(implicit system: ActorSystem, materializer: ActorMaterializer) extends DefaultJsonProtocol {
  import system.dispatcher

  private case class ReadMe(content: String, encoding: String, path: String)
  private implicit val readmeFormat = jsonFormat3(ReadMe)

  private val githubRepos = {
    val poms = Poms.get.collect{ case Success((p, _)) => PomConvert(p) }
    val scmCleanup = new ScmCleanup
    val repos = poms.take(500).flatMap(p => scmCleanup(p).toList).toSet
    repos
  }
  println("github repos")
  private val progress = new ProgressBar("Downloading READMEs", githubRepos.size)
  progress.start()
  println("starting downloads")

  private val token = {
    val home = System.getProperty("user.home")
    val path = home + "/.github/.credentials"
    val source = io.Source.fromFile(path)
    val t = source.mkString
    source.close()
    t
  }

  // using http4s until
  // https://github.com/akka/akka/issues/20408
  // is fixed
  import org.http4s._
  import org.http4s.Http4s._
  import org.http4s.headers.Authorization
  import scalaz.concurrent.Task
  import scala.concurrent.{Future, Promise}
  import scalaz.{-\/, \/-}
  def asFuture[T](task: Task[T]): Future[T] = {
    val p = Promise[T]()
    task.unsafePerformAsync {
      case \/-(a) => {p.success(a); ()}
      case -\/(t) => {p.failure(t); ()}
    }
    p.future
  }
  val client = org.http4s.client.middleware.FollowRedirect(1)(org.http4s.client.blaze.SimpleHttp1Client())
  private def request(gh: GithubRepo): Future[(ReadMe, GithubRepo)] = {
    asFuture(client.fetchAs[String](Request(
      uri = uri("https://api.github.com/repos/mrumkovskis/query/readme"),
      headers = Headers(Authorization(GenericCredentials(s"token $token".ci, Map())))
    ))).map(v => (v.parseJson.convertTo[ReadMe], gh))
  }
  
  private val downloadReadme = Source(githubRepos)
    .mapAsync(1)(request)
    .map{ case (ReadMe(content, encoding, path), GithubRepo(user, repo)) => {
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
    }}

  def run() = {
    Await.result(downloadReadme.runForeach(_ => ()), Duration.Inf)
  }
}