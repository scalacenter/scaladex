package ch.epfl.scala.index

import upickle.default.{Reader, Writer, write => uwrite, read => uread}

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.util.Success

object Server {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val poms = maven.Poms.get.collect{ 
      case Success(p) => maven.PomConvert(p) 
    }
    val simple = poms.map(p => SimpleModel(p.groupId)).distinct

    val api = new Api {
      def search(query: String): List[SimpleModel] = {
        simple.filter(_.groupId.contains(query)).take(50)
      }
    }

    val index = {
      import scalatags.Text.all._
      import scalatags.Text.tags2.{title, noscript}

      "<!DOCTYPE html>" +
      html(
        head(
          title("Scaladex"),
          base(href:="/"),
          meta(charset:="utf-8")
        ),
        body(
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

    val setup = Http().bindAndHandle(route, "localhost", 8080)
    Await.result(setup, 20.seconds)

    ()
  } 
}

object AutowireServer extends autowire.Server[String, Reader, Writer]{
  def read[Result: Reader](p: String) = uread[Result](p)
  def write[Result: Writer](r: Result) = uwrite(r)
}