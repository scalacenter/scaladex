package ch.epfl.scala.index
package data
package maven

import me.tongfei.progressbar._

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

import java.nio.file._
import scala.util._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Success, Failure}

class DownloadParentPoms(implicit system: ActorSystem, materializer: ActorMaterializer) {
  import system.dispatcher

  private def downloadRequest(dep: maven.Dependency) = {
    (HttpRequest(uri = Uri(s"https://repo.jfrog.org/artifactory/libs-release-bintray//${Poms.path(dep)}")), dep)
  }
  private val dlBintrayHttpFlow = Http().cachedHostConnectionPoolHttps[maven.Dependency]("repo.jfrog.org")

  def run(lastFailedToDownload: Int = 0): Unit = {
    val parentPomsToDownload =
      Poms.load().collect {
        case Failure(m: MissingParentPom) => m.dep
      }

    println(s"to download: ${parentPomsToDownload.size}")
    println(s"last failed: $lastFailedToDownload")
    if(parentPomsToDownload.size <= lastFailedToDownload) ()
    else {
      
      val progress2 = new ProgressBar("Downloading POMs", parentPomsToDownload.size)
      progress2.start()

      val downloadPoms =
        Source(parentPomsToDownload)
          .map(downloadRequest)
          .via(dlBintrayHttpFlow)
          .mapAsync(1){ 
            case (Success(response), dep) => 
              Unmarshal(response).to[String].map(body =>
                if(response.status == StatusCodes.OK) Right((body, dep))
                else Left(body)
              )
            case (Failure(e), _) => Future.failed(e)
          }
          .map{ 
            case Right((pom, dep)) => {
              progress2.step()
              val pomPath = Poms.parentPomsBase.resolve(Poms.path(dep))
              Files.createDirectories(pomPath.getParent)
              val printer = new scala.xml.PrettyPrinter(80, 2)
              val pw = new java.io.PrintWriter(pomPath.toFile)
              pw.println(printer.format(scala.xml.XML.loadString(pom)))
              pw.close
              0
            }
            case Left(err) => {
              println(err)
              1
            }
          }

      val failedToDownload = Await.result(downloadPoms.runFold(0)(_ + _), Duration.Inf)
      progress2.stop()
      run(failedToDownload) // grand-parent poms, etc
    }
  }
}