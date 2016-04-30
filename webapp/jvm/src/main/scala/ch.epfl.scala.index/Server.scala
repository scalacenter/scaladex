package ch.epfl.scala.index

import cleanup._
import bintray._

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

    def keep(pom: maven.MavenModel, metas: List[BintraySearch]) = {
      val packagingOfInterest = Set("aar", "jar")
      val typesafeNonOSS = Set(
        "for-subscribers-only",
        "instrumented-reactive-platform",
        "subscribers-early-access"
      )
      packagingOfInterest.contains(pom.packaging) &&
      !metas.exists(meta => meta.owner == "typesafe" && typesafeNonOSS.contains(meta.repo))
    }

    val poms = maven.Poms.get.collect{ case Success((pom, metas)) =>
      (maven.PomConvert(pom), metas) 
    }.filter{ case (pom, metas) => keep(pom, metas)}

    val scmCleanup = new ScmCleanup
    val licenseCleanup = new LicenseCleanup
    
    val artifacts = poms.map{ case (pom, metas) =>
      import pom._

      Artifact(
        name,
        description,
        ArtifactRef(
          groupId,
          artifactId,
          version
        ),
        metas.map(meta => ISO_8601_Date(meta.created.toString)),
        dependencies.map{ dependency =>
          import dependency._
          ArtifactRef(
            groupId,
            artifactId,
            version
          )
        }.toSet,
        scmCleanup(pom),
        licenseCleanup(pom)
      )
    }

    val api = new Api {
      def search(query: String): (Int, List[Artifact]) = {
        val filtered =
          if(query.isEmpty) artifacts
          else artifacts.filter{p =>
            import p._
            import p.ref._

            val githubInfo =
              github.toList.flatMap{ case GithubRepo(user, repo) =>
                List(user, repo)
              }

            (List(groupId, artifactId, version) ::: githubInfo).exists(s => s.contains(query))
          }

        (filtered.size, filtered.take(100))
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