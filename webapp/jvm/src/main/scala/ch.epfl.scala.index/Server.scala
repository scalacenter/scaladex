package ch.epfl.scala.index

import cleanup._
import bintray._

import elastic._
import com.sksamuel.elastic4s._
import ElasticDsl._

import upickle.default.{Reader, Writer, write => uwrite, read => uread}

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Success

object Server {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val api = new Api {
      def find(q: String): Future[(Long, List[Project])] = {
        esClient.execute {
          search.in(indexName / collectionName) query q
        }.map(r => (r.totalHits, r.as[Project].toList))
      }
    }

    val index = {
      import scalatags.Text.all._
      import scalatags.Text.tags2.title

      "<!DOCTYPE html>" +
      html(
        head(
          title("Scaladex"),
          base(href:="/"),
          meta(charset:="utf-8")
        ),
        body(
          script(src:="/assets/webapp-jsdeps.js"),
          script(src:="/assets/webapp-fastopt.js"),
          script("ch.epfl.scala.index.Client().main()")
        )
      )
    }

    val route = {
      import akka.http.scaladsl._
      import server.Directives._

      post {
        path("api" / Segments){ s ⇒
          entity(as[String]) { e ⇒
            complete {
              AutowireServer.route[Api](api)(
                autowire.Core.Request(s, uread[Map[String, String]](e))
              )
            }
          }
        }
      } ~
      get {
        path("assets" / Rest) { path ⇒
          getFromResource(path)
        } ~
        pathSingleSlash {
         complete(HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, index)))
        }
      }
    }

    val setup = for {
      _ <- Http().bindAndHandle(route, "localhost", 8080)
      _ <- esClient.execute { indexExists(indexName) }
    } yield ()
    Await.result(setup, 20.seconds)

    ()
  } 
}

object AutowireServer extends autowire.Server[String, Reader, Writer]{
  def read[Result: Reader](p: String) = uread[Result](p)
  def write[Result: Writer](r: Result) = uwrite(r)
}