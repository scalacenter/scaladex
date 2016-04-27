package ch.epfl.scala.index

import cleanup._

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
    val simple = poms.map{p =>
      import p._

      Artifact(
        ArtifactRef(
          groupId,
          artifactId,
          version
        ),
        dependencies.map{ d =>
          import d._
          ArtifactRef(
            groupId,
            artifactId,
            version
          )
        }.toSet,
        ScmCleanup.find(p),
        LicenseCleanup.find(licenses)
      )
    }

    val api = new Api {
      def search(query: String): List[Artifact] = {

        val filtered =
          if(query.isEmpty) simple
          else simple.filter{p =>
            import p._
            import p.ref._

            val githubInfo =
              github.toList.flatMap{ case GithubRepo(user, repo) =>
                List(user, repo)
              }

            (List(groupId, artifactId, version) ::: githubInfo).exists(s => s.contains(query))
          }

        filtered.take(100)
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