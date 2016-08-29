package ch.epfl.scala.index
package server

import model._
import model.misc._
import data.project.ProjectForm

import release._
import data.github.GithubCredentials
import data.elastic._
import api.Autocompletion

import akka.http.scaladsl._
import model._
import headers.{HttpCredentials, Referer}
import server.Directives._
import server.directives._
import Uri._
import StatusCodes._
import TwirlSupport._

import com.softwaremill.session._
import com.softwaremill.session.CsrfDirectives._
import com.softwaremill.session.CsrfOptions._
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import upickle.default.{write => uwrite}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try
import java.util.UUID
import scala.collection.parallel.mutable.ParTrieMap
import java.lang.management.ManagementFactory

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object Server {
  def main(args: Array[String]): Unit = {

    val port = 
      if(args.isEmpty) 8080
      else args.head.toInt

    val config = ConfigFactory.load().getConfig("org.scala_lang.index.server")
    val production = config.getBoolean("production")

    if(production) {
      val pid = ManagementFactory.getRuntimeMXBean().getName().split("@").head
      val pidFile = Paths.get("PID") 
      Files.write(pidFile, pid.getBytes(StandardCharsets.UTF_8))
      sys.addShutdownHook {
        Files.delete(pidFile)
      }
    }

    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val sessionSecret = config.getString("sesssion-secret")

    val github = new Github
    val users  = ParTrieMap[UUID, UserState]()

    val sessionConfig = SessionConfig.default(sessionSecret)
    implicit def serializer: SessionSerializer[UUID, String] =
      new SingleValueSessionSerializer(
          _.toString(),
          (id: String) => Try { UUID.fromString(id) }
      )
    implicit val sessionManager = new SessionManager[UUID](sessionConfig)
    implicit val refreshTokenStorage = new InMemoryRefreshTokenStorage[UUID] {
      def log(msg: String) =
        if (msg.startsWith("Looking up token for selector")) () // borring
        else println(msg)
    }

    val api = new Api(github)

    def getUser(id: Option[UUID]): Option[UserState] = id.flatMap(users.get)

    def frontPage(userInfo: Option[UserInfo]) = {
      for {
        keywords       <- api.keywords()
        targets        <- api.targets()
        dependencies   <- api.dependencies()
        latestProjects <- api.latestProjects()
        latestReleases <- api.latestReleases()
      } yield views.html.frontpage(keywords, targets, dependencies, latestProjects, latestReleases, userInfo)
    }

    def canEdit(owner: String, repo: String, userState: Option[UserState]) = {
      userState.map(s => s.isAdmin || s.repos.contains(GithubRepo(owner, repo))).getOrElse(false)
    }

    def editPage(owner: String, repo: String, userState: Option[UserState]) = {
      val user = userState.map(_.user)
      if(canEdit(owner, repo, userState)) {
        for {
          keywords <- api.keywords()
          project <- api.project(Project.Reference(owner, repo))
        } yield {
          project.map{p =>
            val allKeywords = (p.keywords ++ keywords.keys.toSet).toList.sorted
            (OK, views.project.html.editproject(p, allKeywords, user))
          }.getOrElse((NotFound, views.html.notfound(user)))
        }
      }
      else Future.successful((Forbidden, views.html.forbidden(user)))
    }

    def projectPage(owner: String,
                    repo: String,
                    artifact: Option[String],
                    version: Option[SemanticVersion],
                    userState: Option[UserState],
                    statusCode: StatusCode = OK) = {

      val user = userState.map(_.user)
      api
        .projectPage(Project.Reference(owner, repo), ReleaseSelection(artifact, version))
        .map(_.map {case (project, options) =>
          import options._
          (statusCode,
           views.project.html.project(
               project,
               artifacts,
               versions,
               targets,
               release,
               user,
               canEdit(owner, repo, userState)
           ))
        }.getOrElse((NotFound, views.html.notfound(user))))
    }

    val publishProcess = new PublishProcess(api, system, materializer)
    /*
     * verifying a login to github
     * @param credentials the credentials
     * @return
     */
    def githubAuthenticator(
        credentialsHeader: Option[HttpCredentials]): Credentials => Future[Option[GithubCredentials]] = {
      case Credentials.Provided(username) => {
        credentialsHeader match {
          case Some(cred) => {
            val upw               = new String(new sun.misc.BASE64Decoder().decodeBuffer(cred.token()))
            val userPass          = upw.split(":")
            val githubCredentials = GithubCredentials(userPass(0), userPass(1))
            // todo - catch errors
            publishProcess
              .authenticate(githubCredentials)
              .map(granted =>
                    if (granted) Some(githubCredentials)
                    else None)
          }
          case _ => Future.successful(None)
        }
      }
      case _ => Future.successful(None)
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
      val size     = segments.size
      val takeFrom = if (segments.head.isEmpty) 1 else 0

      val artifactId = segments(size - 3)
      val version    = segments(size - 2)
      val groupId    = segments.slice(takeFrom, size - 3).mkString(".")

      MavenReference(groupId, artifactId, version)
    }

    val shields = parameters(('color.?, 'style.?, 'logo.?, 'logoWidth.as[Int].?))
    val shieldsSubject = shields & parameters('subject)
 
    def shieldsSvg(rawSubject: String, rawStatus: String, rawColor: Option[String],
      style: Option[String], logo: Option[String], logoWidth: Option[Int]) = {

      def shieldEscape(in: String): String = 
        in
          .replaceAllLiterally("-", "--")
          .replaceAllLiterally("_", "__")

      val subject = shieldEscape(rawSubject)
      val status = shieldEscape(rawStatus)

      val color = rawColor.getOrElse("green") 

      // we need a specific encoding
      val query = List(
        style.map(("style", _)),
        logo.map(l => ("logo", java.net.URLEncoder.encode(l, "ascii").replaceAllLiterally("+", "%2B") )),
        logoWidth.map(w => ("logoWidth", w.toString))
      ).flatten.map{case (k, v) => k + "=" + v }.mkString("?", "&", "")

      redirect(
        s"https://img.shields.io/badge/$subject-$status-$color.svg$query",
        TemporaryRedirect
      )
      
    }

    val route = {
        post {
          path("edit" / Segment / Segment) { (organization, repository) =>
            optionalSession(refreshable, usingCookies) { userId =>
              pathEnd {
                formFieldSeq { fields =>
                  formFields(
                    'contributorsWanted.as[Boolean] ? false,
                    'keywords.*,
                    'defaultArtifact.?,
                    'defaultStableVersion.as[Boolean] ? false,
                    'deprecated.as[Boolean] ? false,
                    'artifactDeprecations.*,
                    'customScalaDoc.?
                  ) { (
                    contributorsWanted,
                    keywords,
                    defaultArtifact,
                    defaultStableVersion,
                    deprecated,
                    artifactDeprecations,
                    customScalaDoc
                  ) =>
                    val name = "documentationLinks"
                    val end = "]".head

                    val documentationLinks =
                      fields
                        .filter{ case (key, _) => key.startsWith(name)}
                        .groupBy{case (key, _) => key.drop("documentationLinks[".length).takeWhile(_ != end)}
                        .values
                        .map{ case Vector((a, b), (c, d)) =>
                          if(a.contains("label")) (b, d)
                          else (d, b)
                        }
                        .toList

                    onSuccess(
                      api.updateProject(
                        Project.Reference(organization, repository),
                        ProjectForm(
                          contributorsWanted,
                          keywords.toSet,
                          defaultArtifact,
                          defaultStableVersion,
                          deprecated,
                          artifactDeprecations.toSet,

                          // documentation
                          customScalaDoc,
                          documentationLinks
                        )
                      )
                    ){ ret =>
                      Thread.sleep(1000) // oh yeah
                      redirect(Uri(s"/$organization/$repository"), SeeOther)
                    }
                  }
                }
              }
            }
          } 
        } ~
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
                  authenticateBasicAsync(realm = "Scaladex Realm", githubAuthenticator(credentials)) { cred =>
                    val publishData = PublishData(path, data, cred, info, contributors, readme, keywords.toSet)

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
            parameter('path) { path =>
              complete {

                /* check if the release already exists - sbt will handle HTTP-Status codes
                 * 404 -> allowed to write
                 * 200 -> only allowed if isSnapshot := true
                 */
                api.maven(mavenPathExtractor(path)) map {

                  case Some(release) => OK
                  case None          => NotFound
                }
              }
            }
          } ~
            path("login") {
              headerValueByType[Referer](){ referer =>
                redirect(Uri("https://github.com/login/oauth/authorize")
                  .withQuery(Query(
                    "client_id" -> github.clientId,
                    "scope" -> "read:org",
                    "state" -> referer.value
                  )),
                  TemporaryRedirect
                )
              }
            } ~
            path("logout") {
              headerValueByType[Referer](){ referer =>
                requiredSession(refreshable, usingCookies) { _ =>
                  invalidateSession(refreshable, usingCookies) { ctx =>
                    ctx.complete(
                      HttpResponse(
                        status = TemporaryRedirect,
                        headers = headers.Location(Uri(referer.value)) :: Nil,
                        entity = HttpEntity.Empty
                      )
                    )
                  }
                }
              }
            } ~
            pathPrefix("callback") {
              path("done") {
                complete("OK")
              } ~
                pathEnd {
                  parameters('code, 'state.?) { (code, state) =>
                    onSuccess(github.info(code)) { userState =>

                      val uuid = java.util.UUID.randomUUID
                      users += uuid -> userState
                      setSession(refreshable, usingCookies, uuid) {
                        setNewCsrfToken(checkHeader) { ctx =>
                          ctx.complete(
                            HttpResponse(
                              status = TemporaryRedirect,
                              headers = headers.Location(Uri(state.getOrElse("/"))) :: Nil,
                              entity = HttpEntity.Empty
                            )
                          )
                        }
                      }
                    }
                  }
                }
            } ~
            path("assets" / Remaining) { path ⇒
              if(path == "reference.conf") complete((Forbidden, ";-)"))
              else getFromResource(path)
            } ~
            path("fonts" / Remaining) { path ⇒
              getFromResource(path)
            } ~
            pathPrefix("api") {
              path("search") {
                get {
                  parameter('q) { query =>
                    complete {
                      api.find(query, page = 1, sorting = None, total = 5).map {
                        case (pagination, projects) =>
                          val summarisedProjects = projects.map(p =>
                                Autocompletion(
                                    p.organization,
                                    p.repository,
                                    p.github.flatMap(_.description).getOrElse("")
                              ))
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
                      api
                        .find(query, page, sorting, you.flatMap(_ => getUser(userId).map(_.repos)).getOrElse(Set()))
                        .map {
                          case (pagination, projects) =>
                            views.html.searchresult(query,
                                                    "search",
                                                    sorting,
                                                    pagination,
                                                    projects,
                                                    getUser(userId).map(_.user),
                                                    !you.isEmpty)
                        }
                  )
                }
              }
            } ~
            path(Segment / Segment / Segment / "latest.svg") { (organization, repository, artifact) =>
              shields { (color, style, logo, logoWidth) =>
                onSuccess(api.projectPage(Project.Reference(organization, repository), 
                  ReleaseSelection(Some(artifact), None))){

                    case Some((_, options)) =>
                      shieldsSvg(artifact, options.release.reference.version.toString(), color, style, logo, logoWidth)
                    case _ => 
                      complete((NotFound, ""))

                }
              }
            } ~
            path("count.svg") {
              parameter('q) { query =>
                shieldsSubject { (color, style, logo, logoWidth, subject) =>
                  onSuccess(api.total(query))(count => 
                    shieldsSvg(subject, count.toString, color, style, logo, logoWidth)
                  )
                }
              }
            } ~
            path("edit" / Segment / Segment) { (organization, repository) =>
              optionalSession(refreshable, usingCookies) { userId =>
                pathEnd {
                  complete(editPage(organization, repository, getUser(userId)))
                }
              }
            } ~
            path(Segment) { organization =>
              optionalSession(refreshable, usingCookies) { userId =>
                parameters('page.as[Int] ? 1, 'sort.?) { (page, sorting) =>
                  pathEnd {
                    val query = s"organization:$organization"
                    complete(
                        api.find(query, page, sorting).map {
                          case (pagination, projects) =>
                            views.html.searchresult(query,
                                                    organization,
                                                    sorting,
                                                    pagination,
                                                    projects,
                                                    getUser(userId).map(_.user),
                                                    you = false)
                        }
                    )
                  }
                }
              }
            } ~
            path(Segment / Segment) { (organization, repository) =>
              optionalSession(refreshable, usingCookies) { userId =>
                parameters('artifact, 'version.?) { (artifact, version) =>
                  val rest = version match {
                    case Some(v) if !v.isEmpty => "/" + v
                    case _                     => ""
                  }
                  redirect(s"/$organization/$repository/$artifact$rest", StatusCodes.PermanentRedirect)
                } ~
                  pathEnd {
                    complete(projectPage(organization, repository, None, None, getUser(userId)))
                  }
              }
            } ~
            path(Segment / Segment / Segment) { (organization, repository, artifact) =>
              optionalSession(refreshable, usingCookies) { userId =>
                complete(projectPage(organization, repository, Some(artifact), None, getUser(userId)))
              }
            } ~
            path(Segment / Segment / Segment / Segment) { (organization, repository, artifact, version) =>
              optionalSession(refreshable, usingCookies) { userId =>
                complete(
                    projectPage(organization, repository, Some(artifact), SemanticVersion(version), getUser(userId)))
              }
            } ~
            pathSingleSlash {
              optionalSession(refreshable, usingCookies) { userId =>
                complete(frontPage(getUser(userId).map(_.user)))
              }
            }
        }
    }

    println("waiting for elastic to start")
    blockUntilYellow()
    println("ready")

    Await.result(Http().bindAndHandle(route, "0.0.0.0", port), 20.seconds)

    println(s"port: $port")


    ()
  }
}
