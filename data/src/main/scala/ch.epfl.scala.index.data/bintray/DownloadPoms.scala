package ch.epfl.scala.index
package data
package bintray

import me.tongfei.progressbar._

import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.http.scaladsl.unmarshalling.Unmarshal

import akka.stream.scaladsl._
import akka.stream.ActorMaterializer
import akka.stream.FlowShape
import akka.actor.ActorSystem

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.Await

import java.nio.file._

class DownloadPoms(implicit system: ActorSystem, materializer: ActorMaterializer) {
  import system.dispatcher

  private def download(search: BintraySearch) = {
    import search._
    def escape(v: String) = v.replace(" ", "%20")
    val downloadUri =
      if(repo == "jcenter" && owner == "bintray") Uri(escape(s"https://jcenter.bintray.com/$path"))
      else Uri(escape(s"https://dl.bintray.com/$owner/$repo/$path"))
    HttpRequest(uri = downloadUri)
  }
  
  val jcenterBintrayHttpFlow = Http().cachedHostConnectionPoolHttps[BintraySearch]("jcenter.bintray.com")
  val dlBintrayHttpFlow      = Http().cachedHostConnectionPoolHttps[BintraySearch]("dl.bintray.com")
  val splitConnectionPool =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      val broadcast = b.add(Broadcast[(HttpRequest, BintraySearch)](2))
      val merge     = b.add(Merge[(Try[HttpResponse], BintraySearch)](2))

      def hostIs(host: String)(in: (HttpRequest, BintraySearch)) = {
        val (request, _) = in
        request.uri.authority.host.toString == host
      }
      broadcast.out(0).filter(hostIs("jcenter.bintray.com")).via(jcenterBintrayHttpFlow) ~> merge.in(0)
      broadcast.out(1).filter(hostIs("dl.bintray.com"))     .via(dlBintrayHttpFlow)      ~> merge.in(1)
      FlowShape(broadcast.in, merge.out)
    })

  private val source = scala.io.Source.fromFile(bintrayCheckpoint.toFile)
  private val searchesBySha1 = BintrayMeta.sortedByCreated(bintrayCheckpoint).
    filter(s => !Files.exists(pomPath(s))). // todo make sure file content matches sha1!
    groupBy(_.sha1).                        // remove duplicates with sha1
    map{case (_, vs) => vs.head}

  private val progress = new ProgressBar("Downloading POMs", searchesBySha1.size)
  
  private def pomPath(search: BintraySearch) = bintrayPomBase.resolve(s"${search.sha1}.pom")
  
  private val downloadPoms = Source(searchesBySha1)
    .map(s => (download(s), s))
    .via(splitConnectionPool)
    .mapAsync(1){
      case (Success(response), search) => {
        Unmarshal(response).to[String].map(body =>
          if(response.status == StatusCodes.OK) Right((body, search))
          else Left((body, search))
        )
      }
      case (Failure(e), _) => Future.failed(e)
    }.map{
      case Right((pom, search)) => {
        progress.step()

        val printer = new scala.xml.PrettyPrinter(80, 2)
        val pw = new java.io.PrintWriter(pomPath(search).toFile)
        pw.println(printer.format(scala.xml.XML.loadString(pom)))
        pw.close
      }
      case Left(e) => println(e)
    }

  def run() = {
    progress.start()
    Await.result(downloadPoms.runForeach(_ => ()), Duration.Inf)
  }
}