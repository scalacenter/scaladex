package ch.epfl.scala.index
package server

import model._
import model.misc.UserInfo
import release.{MavenReference, SemanticVersion}
import data.cleanup.SemanticVersionParser
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
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.directives.Credentials.{Missing, Provided}
import akka.stream.ActorMaterializer
import ch.epfl.scala.index.data.github.GithubCredentials

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

    def frontPage(userInfo: Option[UserInfo]) = {
      for {
        keywords <- api.keywords()
        targets <- api.targets()
        dependencies <- api.dependencies()
        latestProjects <- api.latestProjects()
        latestReleases <- api.latestReleases()
      } yield views.html.frontpage(keywords, targets, dependencies, latestProjects, latestReleases, userInfo)
    }

    def projectPage(owner: String, repo: String, artifact: Option[String] = None,
        version: Option[SemanticVersion], userState: Option[UserState] = None) = {
      val user = userState.map(_.user)
      api.projectPage(Project.Reference(owner, repo), artifact, version).map(
        _.map{ case (project, artifacts, versions, release, targets, releaseCount) =>
          (OK, views.project.html.project(
            project,
            artifacts,
            versions,
            release,
            targets,
            releaseCount,
            user
          ))
        }.getOrElse((NotFound, views.html.notfound(user)))
      )
    }

    val publishProcess = new PublishProcess()

    val route = {
      import akka.http.scaladsl._
      import server.Directives._
      import TwirlSupport._

      def myAuthenticator(credentials: Option[HttpCredentials]): Authenticator[GithubCredentials] = {

        case p@Provided(username) =>

          credentials match {
            case None => None
            case Some(cred) =>

              val upw = new String(new sun.misc.BASE64Decoder().decodeBuffer(cred.token()))
              val userPass = upw.split(":")
              val githubCredentials = GithubCredentials(userPass(0), userPass(1))
              // todo - catch errors
              if (publishProcess.authenticate(githubCredentials)) {

                Some(githubCredentials)
              } else {

                None
              }
          }
        case Missing => None
      }

      /**
       * extract a maven reference from path like
       * /com/github/scyks/playacl_2.11/0.8.0/playacl_2.11-0.8.0.pom =>
       * MavenReference("com.github.scyks", "playacl_2.11", "0.8.0")
       * @param path the real publishing path
       * @return MavenReference
       */
      def mavenPathExtractor(path: String): MavenReference = {

        val segments = path.split("/").toList
        val size = segments.size
        val takeFrom = if(segments.head.isEmpty) 1 else 0

        val artifactId = segments(size - 3)
        val version = segments(size - 2)
        val groupId = segments.slice(takeFrom, size - 3).mkString(".")

        MavenReference(groupId, artifactId, version)
      }

//      rest.route ~
      put {
        path("publish") {
          parameters(
            'path,
            'readme.as[Boolean] ? true,
            'contributors.as[Boolean] ? true,
            'info.as[Boolean] ? true,
            'keywords.as[String].*
          ) { (path, readme, contributors, info, keywords) =>
            entity(as[String]) { str =>
              extractCredentials { credentials =>
                authenticateBasic(realm = "scaladex Realm", myAuthenticator(credentials)) { cred =>

                  publishProcess.writeFiles(path, str, cred)
                  complete(Created)
                }
              }
            }
          }
        }
      } ~
      get {
        path("publish") {
          parameters(
            'path,
            'readme.as[Boolean] ? true,
            'contributors.as[Boolean] ? true,
            'info.as[Boolean] ? true,
            'keywords.as[String].*
          ) { (path, readme, contributors, info, keywords) =>

            complete {

              /* check if the release already exists - sbt will handle HTTP-Status codes
               * 404 -> allowed to write
               * 200 -> only allowed if isSnapshot := true
               */
              api.maven(mavenPathExtractor(path)) map {

                case Some(release) => OK
                case None => NotFound
              }
            }
          }
        } ~
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
                    views.html.searchresult(query, "search", sorting, pagination, projects, userState.map(_.user))
                  }
              }
            }
          }
        } ~
        path(Segment) { owner =>
          optionalSession(refreshable, usingCookies) { userState =>
            parameters('page.as[Int] ? 1, 'sort.?) { (page, sorting) =>
              complete {
                api.organization(owner, page, sorting)
                  .map { case (pagination, projects) =>
                    views.html.searchresult(owner, owner,sorting, pagination, projects, userState.map(_.user))
                  }
              }
            }
          }
        } ~
        path(Segment / Segment) { (owner, repo) =>
          optionalSession(refreshable, usingCookies) { userState =>
            parameters('artifact, 'version.?){ (artifact, version) =>
              val rest = version match {
                case Some(v) if !v.isEmpty => "/" + v
                case _ => ""
              }
              redirect(s"/$owner/$repo/$artifact$rest", StatusCodes.PermanentRedirect)
            } ~
            pathEnd {
              complete(projectPage(owner, repo, None, None, userState))
            }
          }
        } ~
        path(Segment / Segment / Segment ) { (owner, repo, artifact) =>
          optionalSession(refreshable, usingCookies) { userState =>
            complete(projectPage(owner, repo, Some(artifact), None, userState))
          }
        } ~
        path(Segment / Segment / Segment / Segment) { (owner, repo, artifact, version) =>
          optionalSession(refreshable, usingCookies) { userState =>
            complete(projectPage(owner, repo, Some(artifact), SemanticVersionParser(version), userState))
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
