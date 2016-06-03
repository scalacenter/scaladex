package ch.epfl.scala.index
package data
package github

import cleanup.GithubRepoExtractor
import model.GithubRepo
import maven.PomsReader

import me.tongfei.progressbar._

import org.json4s._
import native.Serialization.writePretty
import de.heikoseeberger.akkahttpjson4s.Json4sSupport

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._, headers._
import akka.http.scaladsl.unmarshalling.Unmarshal

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.ActorMaterializer

import scala.util.{Success, Failure}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import java.nio.file.Files
import java.nio.charset.{StandardCharsets}

object Json4s extends Json4sSupport {
  implicit val formats = DefaultFormats
  implicit val serialization = native.Serialization
}

class GithubDownload(implicit system: ActorSystem, materializer: ActorMaterializer) {
  import system.dispatcher
  
  private val githubHttpFlow = Http().cachedHostConnectionPoolHttps[GithubRepo]("api.github.com")

  private val githubRepoExtractor = new GithubRepoExtractor
  private val githubRepos =
    PomsReader.load()
      .collect {case Success((pom, _)) => githubRepoExtractor(pom)}
      .flatten
      .map{ case GithubRepo(owner, repo) => GithubRepo(owner.toLowerCase, repo.toLowerCase) }
      .toSet

  private val credentials = {
    val tokens = Array(
      "5e2ddeed0f9c6169d868121330599b8353ab0b55",
      "6e7364f7db333be44b5aa0416f5a0b33d8743b14",
      "6518021b0dbf82757717c9793906425adc779eb3"
    )
    val token = tokens(scala.util.Random.nextInt(tokens.size))
    Authorization(GenericHttpCredentials("token", token))
  }

  private def readme(github: GithubRepo): (HttpRequest, GithubRepo) = {
    val GithubRepo(user, repo) = github
    val request =
      HttpRequest(
        uri = Uri(s"https://api.github.com/repos/$user/$repo/readme"),
        headers = List(
          credentials,
          Accept(List(MediaRange.custom("application/vnd.github.VERSION.html")))
        )
      )
    (request, github)
  }

  private val downloadReadme = Source(githubRepos)
    .map(r => readme(r))
    .via(githubHttpFlow)
    .mapAsync(1){
      case (Success(response), github) => {
        Unmarshal(response).to[String].map(body =>
          if(response.status == StatusCodes.OK) Right((body, github))
          else Left((body, github))
        )
      }
      case (Failure(e), _) => Future.failed(e)
    }.map{
      case Right((readme, github)) => {
        val dir = path(github)
        Files.createDirectories(dir)
        Files.write(
          githubReadmePath(github),
           // repo info contains default branch
          GithubReadme.absoluteUrl(readme, github, "master").getBytes(StandardCharsets.UTF_8)
        )
      }
      case Left(e) => println(e)
    }

  private def repo(github: GithubRepo): (HttpRequest, GithubRepo) = {
    val GithubRepo(user, repo) = github
    val request =
      HttpRequest(
        uri = Uri(s"https://api.github.com/repos/$user/$repo"),
        headers = List(
          credentials,
          Accept(MediaTypes.`application/json`)
        )
      )
    (request, github)
  }

  private val downloadRepoInfo = {
    import Json4s._

    Source(githubRepos)
      .map(r => repo(r))
      .via(githubHttpFlow)
      .mapAsync(1){
        case (Success(response), github) => {
          if(response.status == StatusCodes.OK) {
            Unmarshal(response).to[Repository].map{ repo =>
              Right((repo, github))
            }
          } else {
            // handle redirects: https://github.com/akka/akka/issues/15990
            Unmarshal(response).to[JObject].map(body =>
              Left((body, github))
            )
          }
        }
        case (Failure(e), _) => {
          println("failure")
          Future.failed(e)
        }
      }.map{
        case Right((repo, github)) => {
          val dir = path(github)
          Files.createDirectories(dir)

          Files.write(
            githubRepoInfoPath(github),
            writePretty(repo).getBytes(StandardCharsets.UTF_8)
          )
        }
        case Left(e) => println(e)
      }
  }

  def run() = {
    val progress1 = new ProgressBar("Downloading Repo Infos", githubRepos.size)
    progress1.start()
    Await.result(downloadRepoInfo.runForeach(_ => progress1.step()), Duration.Inf)
    progress1.stop()

    val progress2 = new ProgressBar("Downloading READMEs", githubRepos.size)
    progress2.start()
    Await.result(downloadReadme.runForeach(_ => progress2.step()), Duration.Inf)
    progress2.stop()   
  }
}























