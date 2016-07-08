package ch.epfl.scala.index
package server

import model._
import model.misc.UserInfo
import model.release.SemanticVersion
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

    // owner: akka
    // part2: akka (project) or akka-http (artifact)
    def projectPage(owner: String, part2: String, version: Option[SemanticVersion], user: Option[UserInfo]) = {
      val projectRef = Project.Reference(owner, part2)

      val projectAndReleases =
        for {
          project <- api.project(projectRef)
          releases <- api.releases(projectRef)
        } yield (project, releases)

      def finds[A, B](xs: List[(A, B)], fs: List[A => Boolean]): Option[(A, B)] = {
        fs match {
          case Nil => None
          case f :: h =>
            xs.find{ case (a, b) => f(a) } match {
              case None => finds(xs, h)
              case s    => s
            }
        }
      }

      def defaultRelease(project: Project, releases: List[Release]): Option[(Release, List[SemanticVersion])] = {
        val artifacts = releases.groupBy(_.reference.artifact).toList
        
        def projectDefault(artifact: String): Boolean = Some(artifact) == project.defaultArtifact
        def core(artifact: String): Boolean = artifact.endsWith("-core")
        def projectRepository(artifact: String): Boolean = project.reference.repository == artifact

        def alphabetically = artifacts.sortBy(_._1).headOption

        (finds(artifacts, List(projectDefault _, core _, projectRepository _)) match {
          case None => alphabetically
          case x => x
        }).flatMap{ case (_, releases) =>
          val sortedByVersion = releases.sortBy(_.reference.version)(Descending)

          // select last non preRelease release if possible (ex: 1.1.0 over 1.2.0-RC1)
          val lastNonPreRelease =
            if(sortedByVersion.exists(_.reference.version.preRelease.isEmpty)){
              sortedByVersion.filter(_.reference.version.preRelease.isEmpty)
            } else {
              sortedByVersion
            }

          // select latest stable target if possible (ex: scala 2.11 over 2.12 and scala-js)
          val latestStableTarget =
            (
              if(lastNonPreRelease.exists(_.reference.target.scalaJsVersion.isEmpty)) {
                lastNonPreRelease.filter(_.reference.target.scalaJsVersion.isEmpty)
              } else lastNonPreRelease
            ).sortBy(_.reference.target.scalaVersion).headOption

          latestStableTarget.headOption.map(r =>
            (r, sortedByVersion.map(_.reference.version).distinct)
          )
        }
      }

      projectAndReleases.map{ case (p, releases) =>
        p.flatMap(project =>
          defaultRelease(project, releases).map{ case (selectedRelease, versions) =>
            val artifacts = releases.groupBy(_.reference.artifactReference).keys.toList.map(_.artifact)

            (OK, views.project.html.project(
              project,
              artifacts,
              versions,
              selectedRelease,
              releases.size,
              user
            ))
          }
        ).getOrElse((NotFound, views.html.notfound(user)))
      }
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
        path(Segment / Segment) { (owner, part2) =>
          optionalSession(refreshable, usingCookies) { userState =>
            complete(projectPage(owner, part2, version = None, userState.map(_.user)))
          }
        } ~
        // path(Segment / Segment / Segment) { (owner, artifactName, version) =>
        //   optionalSession(refreshable, usingCookies) { userState =>
        //     val reference = Artifact.Reference(owner, artifactName)
        //     complete(artifactPage(reference, SemanticVersionParser(version), userState.map(_.user)))
        //   }
        // } ~
        // path("edit" / Segment / Segment) { (owner, artifactName) =>
        //   optionalSession(refreshable, usingCookies) { userState =>
        //     val reference = Artifact.Reference(owner, artifactName)
        //     complete(
        //       api.projectPage(reference).map(project =>
        //         project.map(p => views.html.editproject(p, reference, version = None, userState.map(_.user)))
        //       )
        //     )
        //   }
        // } ~
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
