package ch.epfl.scala.index
package server

import model._
import model.misc.UserInfo
// import data.cleanup.SemanticVersionParser
import data.elastic._

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import Uri._
import StatusCodes._
import com.softwaremill.session._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await

object Server {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val github = new Github

    val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
    implicit val sessionManager = new SessionManager[UserState](sessionConfig)
    implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UserState] {
      def log(msg: String) = println(msg)
    }

    val api = new Api(github)
    // val rest = new RestApi(api)

    def frontPage(userInfo: Option[UserInfo]) = {
      for {
        keywords <- api.keywords()
        targets <- api.targets()
        dependencies <- api.dependencies()
        latestProjects <- api.latestProjects()
        latestReleases <- api.latestReleases()
      } yield views.html.frontpage(keywords, targets, dependencies, latestProjects, latestReleases, userInfo)
    }

    val route = {
      import akka.http.scaladsl._
      import server.Directives._
      import TwirlSupport._

      // rest.route ~
      get {
        path("login") {
          redirect(Uri("https://github.com/login/oauth/authorize").withQuery(Query(
            "client_id" -> github.clientId
          )),TemporaryRedirect)
        } ~
        path("logout") {
          requiredSession(refreshable, usingCookies) { _ =>
            invalidateSession(refreshable, usingCookies) { ctx =>
              ctx.complete(frontPage(None))
            }
          }
        } ~
        pathPrefix("callback") {
          path("done") {
            complete("OK")
          } ~
          pathEnd {
            parameter('code) { code =>
              val userState = Await.result(github.info(code), 10.seconds)
              setSession(refreshable, usingCookies, userState) {
                setNewCsrfToken(checkHeader) { ctx =>
                  ctx.complete(frontPage(Some(userState.user)))
                }
              }
            }
          }
        } ~
        path("assets" / Remaining) { path ⇒
          getFromResource(path)
        } ~
        path("fonts" / Remaining) { path ⇒
          getFromResource(path)
        } ~
        path("search") {
          optionalSession(refreshable, usingCookies) { userState =>
            parameters('q, 'page.as[Int] ? 1, 'sort.?, 'you.?) { (query, page, sorting, you) =>
              complete {
                // you.flatMap(_ => userState.map(_.repos))
                api.find(query, page, sorting)
                  .map { case (pagination, projects) =>
                    views.html.searchresult(query, sorting, pagination, projects, userState.map(_.user))
                  }
              }
            }
          }
        } ~
        path("test" / Segment / Segment){ (t1, t2) =>
          optionalSession(refreshable, usingCookies) { userState =>
            parameters('page.as[Int] ? 1, 'sort.?) { (page, sorting) =>
              complete {
                api.dependent(t1 + "/" + t2, page, sorting)
                  .map { case (pagination, projects) =>
                    views.html.searchresult("", sorting, pagination, projects, userState.map(_.user))
                  }
              }
            }
          }
        } ~
        path(Segment) { owner =>
          optionalSession(refreshable, usingCookies) { userState =>
            parameters('artifact, 'version.?){ (artifact, version) =>
              val rest = version match {
                case Some(v) if !v.isEmpty => "/" + v
                case _ => ""
              }
              redirect(s"/$owner/$artifact$rest", StatusCodes.PermanentRedirect)
            } ~
            parameters('page.as[Int] ? 1, 'sort.?) { (page, sorting) =>
              complete {
                api.organization(owner, page, sorting)
                  .map { case (pagination, projects) =>
                    views.html.searchresult(owner, sorting, pagination, projects, userState.map(_.user))
                  }
              }
            }
          }
        } ~
        path(Segment / Segment) { (owner, repo) =>
          optionalSession(refreshable, usingCookies) { userState =>
            val projectRef = Project.Reference(owner, repo)
            val user = userState.map(_.user)

            complete(api.projectPage(projectRef).map(
              _.map{ case (project, artifacts, versions, release, releaseCount) =>
                (OK, views.project.html.project(
                  project,
                  artifacts,
                  versions,
                  release,
                  releaseCount,
                  user
                ))
              }.getOrElse((NotFound, views.html.notfound(user))))
            )
          }
        } ~
        path(Segment / Segment / Segment) { (owner, artifact, version) =>
          optionalSession(refreshable, usingCookies) { userState =>
            // val reference = Artifact.Reference(owner, artifactName)
            // complete(artifactPage(reference, SemanticVersionParser(version), userState.map(_.user)))
            complete("OK")
          }
        } ~
        path("edit" / Segment / Segment) { (owner, artifactName) =>
          optionalSession(refreshable, usingCookies) { userState =>
            complete("OK")
          }
        } ~
        pathSingleSlash {
          optionalSession(refreshable, usingCookies) { userState =>
            complete(frontPage(userState.map(_.user)))
          }
        }
      }
    }

    println("waiting for elastic to start")
    blockUntilYellow()
    println("ready")

    Await.result(Http().bindAndHandle(route, "0.0.0.0", 8080), 20.seconds)

    ()
  }
}
