package scaladex.server.route.api

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.softwaremill.session.SessionDirectives.optionalSession
import com.softwaremill.session.SessionOptions.refreshable
import com.softwaremill.session.SessionOptions.usingCookies
import endpoints4s.akkahttp.server
import play.api.libs.json._
import scaladex.core.api.AutocompletionEndpoints
import scaladex.core.api.AutocompletionResponse
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactSelection
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.ScalaLanguageVersion
import scaladex.core.model.search.ProjectHit
import scaladex.core.model.search.SearchParams
import scaladex.core.service.SearchEngine
import scaladex.core.service.WebDatabase
import scaladex.server.GithubUserSession

object SearchApi {
  implicit val formatProject: OFormat[Project] =
    Json.format[Project]

  implicit val formatArtifactOptions: OFormat[ArtifactOptions] =
    Json.format[ArtifactOptions]

  case class Project(
      organization: String,
      repository: String,
      logo: Option[String] = None,
      artifacts: Seq[String] = Nil
  )

  case class ArtifactOptions(
      artifacts: Seq[String],
      versions: Seq[String],
      groupId: String,
      artifactId: String,
      version: String
  )
  private[api] def parseScalaTarget(
      targetType: Option[String],
      scalaVersion: Option[String],
      scalaJsVersion: Option[String],
      scalaNativeVersion: Option[String],
      sbtVersion: Option[String]
  ): Option[Platform] =
    (
      targetType,
      scalaVersion.flatMap(ScalaLanguageVersion.tryParse),
      scalaJsVersion.flatMap(BinaryVersion.parse),
      scalaNativeVersion.flatMap(BinaryVersion.parse),
      sbtVersion.flatMap(BinaryVersion.parse)
    ) match {

      case (Some("JVM"), Some(scalaVersion), _, _, _) =>
        Some(Platform.ScalaJvm(scalaVersion))

      case (Some("JS"), Some(scalaVersion), Some(scalaJsVersion), _, _) =>
        Some(Platform.ScalaJs(scalaVersion, scalaJsVersion))

      case (
            Some("NATIVE"),
            Some(scalaVersion),
            _,
            Some(scalaNativeVersion),
            _
          ) =>
        Some(Platform.ScalaNative(scalaVersion, scalaNativeVersion))

      case (Some("SBT"), Some(scalaVersion), _, _, Some(sbtVersion)) =>
        Some(Platform.SbtPlugin(scalaVersion, sbtVersion))

      case (Some("Java"), None, None, None, None) => Some(Platform.Java)
      case _                                      => None
    }
}

class SearchApi(searchEngine: SearchEngine, database: WebDatabase, session: GithubUserSession)(
    implicit val executionContext: ExecutionContext
) extends PlayJsonSupport {
  import session.implicits._

  val routes: Route =
    pathPrefix("api") {
      cors() {
        path("search") {
          get {
            parameters(
              "q",
              "target",
              "scalaVersion",
              "page".as[Int].?,
              "total".as[Int].?,
              "scalaJsVersion".?,
              "scalaNativeVersion".?,
              "sbtVersion".?,
              "cli".as[Boolean] ? false
            ) {
              (
                  q,
                  targetType,
                  scalaVersion,
                  page,
                  total,
                  scalaJsVersion,
                  scalaNativeVersion,
                  sbtVersion,
                  cli
              ) =>
                val platform = SearchApi.parseScalaTarget(
                  Some(targetType),
                  Some(scalaVersion),
                  scalaJsVersion,
                  scalaNativeVersion,
                  sbtVersion
                )

                def convert(project: ProjectHit): SearchApi.Project = {
                  import project.document._
                  SearchApi.Project(
                    organization.value,
                    repository.value,
                    githubInfo.flatMap(_.logo.map(_.target)),
                    artifactNames.map(_.value)
                  )
                }

                platform match {
                  case Some(_) =>
                    val searchParams = SearchParams(
                      queryString = q,
                      targetFiltering = platform,
                      cli = cli,
                      page = page.getOrElse(0),
                      total = total.getOrElse(10)
                    )
                    val result = searchEngine
                      .find(searchParams)
                      .map(page => page.items.map(p => convert(p)))
                    complete(OK, result)

                  case None =>
                    val errorMessage =
                      s"something is wrong: $platform $scalaVersion $scalaJsVersion $scalaNativeVersion $sbtVersion"
                    complete(BadRequest, errorMessage)
                }
            }
          }
        } ~
          path("project") {
            get {
              parameters(
                "organization",
                "repository",
                "artifact".?,
                "target".?,
                "scalaVersion".?,
                "scalaJsVersion".?,
                "scalaNativeVersion".?,
                "sbtVersion".?
              ) {
                (
                    organization,
                    repository,
                    artifact,
                    targetType,
                    scalaVersion,
                    scalaJsVersion,
                    scalaNativeVersion,
                    sbtVersion
                ) =>
                  val reference =
                    Project.Reference.from(organization, repository)
                  val scalaTarget = SearchApi.parseScalaTarget(
                    targetType,
                    scalaVersion,
                    scalaJsVersion,
                    scalaNativeVersion,
                    sbtVersion
                  )
                  complete {
                    getArtifactOptions(reference, scalaTarget, artifact)
                  }
              }
            }
          }
      }
    } ~ cors() {
      AutocompletionApi.autocomplete.implementedByAsync {
        case AutocompletionApi.WithSession(request, userId) =>
          val user = session.getUser(userId)
          autocomplete(request.searchParams(user))
      }
    }

  private def getArtifactOptions(
      projectRef: Project.Reference,
      scalaTarget: Option[Platform],
      artifact: Option[String]
  ): Future[Option[SearchApi.ArtifactOptions]] = {
    val selection = new ArtifactSelection(
      target = scalaTarget,
      artifactNames = artifact.map(Artifact.Name.apply),
      version = None,
      selected = None
    )
    for {
      projectOpt <- database.getProject(projectRef)
      artifacts <- database.getArtifacts(projectRef)
    } yield for {
      project <- projectOpt
      filteredArtifacts = selection.filterArtifacts(artifacts, project)
      selected <- filteredArtifacts.headOption
    } yield {
      val artifacts = filteredArtifacts.map(_.artifactName).distinct
      val versions = filteredArtifacts.map(_.version).distinct
      SearchApi.ArtifactOptions(
        artifacts.map(_.value),
        versions.map(_.toString),
        selected.groupId.value,
        selected.artifactId,
        selected.version.toString
      )
    }
  }

  private def autocomplete(params: SearchParams) =
    for (projects <- searchEngine.autocomplete(params, 5))
      yield projects.map { project =>
        AutocompletionResponse(
          project.organization.value,
          project.repository.value,
          project.githubInfo.flatMap(_.description).getOrElse("")
        )
      }

  object AutocompletionApi extends AutocompletionEndpoints with server.Endpoints with server.JsonEntitiesFromSchemas {
    // On the server-side, the session is made of a user id
    case class WithSession[A](data: A, maybeUserId: Option[UUID])
    def withOptionalSession[A](request: Request[A]): Request[WithSession[A]] =
      new Request[WithSession[A]] {
        val directive: Directive1[WithSession[A]] = Directive[Tuple1[WithSession[A]]] { k =>
          optionalSession(refreshable, usingCookies) { maybeUserId =>
            request.directive(a => k(Tuple1(WithSession(a, maybeUserId))))
          }
        }
        def uri(session: WithSession[A]): Uri = request.uri(session.data)
      }
  }

}
