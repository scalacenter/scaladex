package ch.epfl.scala.index
package server

import model._
import model.misc._
import release._
import data.github.GithubCredentials
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
import upickle.default.{write => uwrite}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

import java.util.UUID
import scala.collection.parallel.mutable.ParTrieMap

object Server {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val github = new Github
    val users = ParTrieMap[UUID, UserState]()

    val sessionConfig = SessionConfig.default("c05ll3lesrinf39t7mc5h6un6r0c69lgfno69dsak3vabeqamouq4328cuaekros401ajdpkh60rrtpd8ro24rbuqmgtnd1ebag6ljnb65i8a55d482ok7o0nch0bfbe")
    implicit def serializer: SessionSerializer[UUID, String] = new SingleValueSessionSerializer(
      _.toString(),
      (id: String) => Try { UUID.fromString(id) }
    )
    implicit val sessionManager = new SessionManager[UUID](sessionConfig)
    implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UUID] {
      def log(msg: String) = 
        if(msg.startsWith("Looking up token for selector")) () // borring
        else println(msg)
    }

    val api = new Api(github)

    def getUser(id: Option[UUID]): Option[UserState] = id.flatMap(users.get)

    def frontPage(userInfo: Option[UserInfo]) = {
      for {
        keywords <- api.keywords()
        targets <- api.targets()
        dependencies <- api.dependencies()
        latestProjects <- api.latestProjects()
        latestReleases <- api.latestReleases()
      } yield
        views.html.frontpage(keywords,
                             targets,
                             dependencies,
                             latestProjects,
                             latestReleases,
                             userInfo)
    }

    def projectPage(owner: String, repo: String, artifact: Option[String] = None,
        version: Option[SemanticVersion], userState: Option[UserState] = None, edit: Boolean = false) = {
      val user = userState.map(_.user)
      val canEdit = userState.map(s =>
        s.isAdmin || s.repos.contains(GithubRepo(owner, repo))
      ).getOrElse(false)
      api.projectPage(Project.Reference(owner, repo), ReleaseSelection(artifact, version)).map(
        _.map{ case (project, releaseCount, options) =>
          import options._

          if(edit){
            (OK, views.project.html.editproject(
              project,
              artifacts,
              versions,
              targets,
              release,
              releaseCount,
              user
            ))
          } else {
            (OK, views.project.html.project(
              project,
              artifacts,
              versions,
              targets,
              release,
              releaseCount,
              user,
              canEdit
            ))
          }
        }.getOrElse((NotFound, views.html.notfound(user)))
      )
    }

    val publishProcess = new PublishProcess(api, system, materializer)

    val route = {
      import akka.http.scaladsl._
      import server.Directives._
      import TwirlSupport._

      /*
       * verifying a login to github
       * @param credentials the credentials
       * @return
       */
      def githubAuthenticator(credentials: Option[HttpCredentials])
        : Authenticator[GithubCredentials] = {

        case p @ Provided(username) =>
          credentials match {
            case None => None
            case Some(cred) =>
              val upw = new String(
                  new sun.misc.BASE64Decoder().decodeBuffer(cred.token()))
              val userPass = upw.split(":")
              val githubCredentials =
                GithubCredentials(userPass(0), userPass(1))
              // todo - catch errors
              if (publishProcess.authenticate(githubCredentials)) {

                Some(githubCredentials)
              } else {

                None
              }
          }
        case Missing => None
      }

      /*
       * extract a maven reference from path like
       * /com/github/scyks/playacl_2.11/0.8.0/playacl_2.11-0.8.0.pom =>
       * MavenReference("com.github.scyks", "playacl_2.11", "0.8.0")
       *
       * @param path the real publishing path
       * @return MavenReference
       */
      def mavenPathExtractor(path: String): MavenReference = {

        val segments = path.split("/").toList
        val size = segments.size
        val takeFrom = if (segments.head.isEmpty) 1 else 0

        val artifactId = segments(size - 3)
        val version = segments(size - 2)
        val groupId = segments.slice(takeFrom, size - 3).mkString(".")

        MavenReference(groupId, artifactId, version)
      }

      put {
        path("publish") {
          parameters(
              'path,
              'readme.as[Boolean] ? true,
              'contributors.as[Boolean] ? true,
              'info.as[Boolean] ? true,
              'keywords.as[String].*
          ) { (path, readme, contributors, info, keywords) =>
            entity(as[String]) { data =>
              extractCredentials { credentials =>
                authenticateBasic(realm = "Scaladex Realm",
                                  githubAuthenticator(credentials)) { cred =>
                  val publishData = PublishData(path,
                                                data,
                                                cred,
                                                info,
                                                contributors,
                                                readme,
                                                keywords.toList)

                  complete {

                    if (publishData.isPom) {

                      publishProcess.writeFiles(publishData)

                    } else {

                      Future(Created)
                    }
                  }
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
              println(s"GET $path")
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
          }
        } ~
        path("login") {
          redirect(Uri("https://github.com/login/oauth/authorize").withQuery(Query(
            "client_id" -> github.clientId,
            // this is required to see all repo with write permissions
            "scope" -> "public_repo"
          )), TemporaryRedirect)
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
              onSuccess(github.info(code)) { userState =>
                val uuid = java.util.UUID.randomUUID
                users += uuid -> userState
                setSession(refreshable, usingCookies, uuid) {
                  setNewCsrfToken(checkHeader) { ctx =>
                    ctx.complete(frontPage(Some(userState.user)))
                  }
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
        pathPrefix("api") {
          path("search") {
            get {
              parameters('q, 'page.as[Int] ? 1, 'sort.?, 'you.?) {
                (query, page, sorting, you) =>
                  complete {
                    api.find(query, page, sorting).map {
                      case (pagination, projects) =>
                        val summarisedProjects = projects.take(5).map(p =>
                            p.reference ->
                            p.github.flatMap(_.description).getOrElse(""))
                        uwrite(summarisedProjects)
                    }
                  }
              }
            }
          }
        } ~
        path("search") {
          optionalSession(refreshable, usingCookies) { userId =>
            parameters('q, 'page.as[Int] ? 1, 'sort.?, 'you.?) { (query, page, sorting, you) =>
              complete(
                api.find(query, page, sorting, you.flatMap(_ => getUser(userId).map(_.repos)).getOrElse(Set())).map{ case (pagination, projects) =>
                  views.html.searchresult(query, "search", sorting, pagination, projects, getUser(userId).map(_.user))
                }
              )
            }
          }
        } ~
        path("edit" / Segment / Segment) { (organization, repository) =>
          optionalSession(refreshable, usingCookies) { userId =>
            pathEnd {
              complete(projectPage(organization, repository, None, None, getUser(userId), edit = true))
            }
          }
        } ~
        path(Segment) { organization =>
          optionalSession(refreshable, usingCookies) { userId =>
            parameters('page.as[Int] ? 1, 'sort.?) { (page, sorting) =>
              pathEnd {
                val query = s"organization:$organization"
                complete(
                  api.find(query, page, sorting).map { case (pagination, projects) =>
                    views.html.searchresult(query, organization, sorting, pagination, projects, getUser(userId).map(_.user))
                  }
                )
              }
            }
          }
        } ~
        path(Segment / Segment) { (organization, repository) =>
          optionalSession(refreshable, usingCookies) { userId =>
            parameters('artifact, 'version.?){ (artifact, version) =>
              val rest = version match {
                case Some(v) if !v.isEmpty => "/" + v
                case _ => ""
              }
              redirect(s"/$organization/$repository/$artifact$rest", StatusCodes.PermanentRedirect)
            } ~
            pathEnd {
              complete(projectPage(organization, repository, None, None, getUser(userId)))
            }
          }
        } ~
        path(Segment / Segment / Segment ) { (organization, repository, artifact) =>
          optionalSession(refreshable, usingCookies) { userId =>
            complete(projectPage(organization, repository, Some(artifact), None, getUser(userId)))
          }
        } ~
        path(Segment / Segment / Segment / Segment) { (organization, repository, artifact, version) =>
          optionalSession(refreshable, usingCookies) { userId =>
            complete(projectPage(organization, repository, Some(artifact), SemanticVersion(version), getUser(userId)))
          }
        } ~
        pathSingleSlash {
          optionalSession(refreshable, usingCookies) { userId =>
            complete(frontPage(getUser(userId).map(_.user)))
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
