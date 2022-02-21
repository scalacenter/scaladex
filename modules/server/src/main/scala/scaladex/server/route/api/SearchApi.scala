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
import scaladex.core.model.Java
import scaladex.core.model.Jvm
import scaladex.core.model.Project
import scaladex.core.model.SbtPlugin
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative
import scaladex.core.model.SemanticVersion
import scaladex.core.model.search.PageParams
import scaladex.core.model.search.ProjectDocument
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
  private[api] def parseBinaryVersion(
      targetType: Option[String],
      scalaVersion: Option[String],
      scalaJsVersion: Option[String],
      scalaNativeVersion: Option[String],
      sbtVersion: Option[String]
  ): Option[BinaryVersion] = {
    val binaryVersion = (targetType, scalaVersion, scalaJsVersion, scalaNativeVersion, sbtVersion) match {
      case (Some("JVM"), Some(sv), _, _, _) =>
        SemanticVersion.parse(sv).map(sv => BinaryVersion(Jvm, Scala(sv)))

      case (Some("JS"), Some(sv), Some(jsv), _, _) =>
        for {
          sv <- SemanticVersion.parse(sv)
          jsv <- SemanticVersion.parse(jsv)
        } yield BinaryVersion(ScalaJs(jsv), Scala(sv))

      case (Some("NATIVE"), Some(sv), _, Some(snv), _) =>
        for {
          sv <- SemanticVersion.parse(sv)
          snv <- SemanticVersion.parse(snv)
        } yield BinaryVersion(ScalaNative(snv), Scala(sv))

      case (Some("SBT"), Some(sv), _, _, Some(sbtv)) =>
        for {
          sv <- SemanticVersion.parse(sv)
          sbtv <- SemanticVersion.parse(sbtv)
        } yield BinaryVersion(SbtPlugin(sbtv), Scala(sv))

      case (Some("JVM"), None, None, None, None) => Some(BinaryVersion(Jvm, Java))
      case _                                     => None
    }
    binaryVersion.filter(_.isValid)
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
              "scalaVersion".?,
              "page".as[Int].withDefault(1),
              "total".as[Int].withDefault(20),
              "scalaJsVersion".?,
              "scalaNativeVersion".?,
              "sbtVersion".?,
              "cli".as[Boolean] ? false
            ) { (q, targetType, scalaVersion, page, total, scalaJsVersion, scalaNativeVersion, sbtVersion, cli) =>
              val binaryVersion = SearchApi.parseBinaryVersion(
                Some(targetType),
                scalaVersion,
                scalaJsVersion,
                scalaNativeVersion,
                sbtVersion
              )
              val pageParams = PageParams(page, total)

              def convert(project: ProjectDocument): SearchApi.Project = {
                SearchApi.Project(
                  project.organization.value,
                  project.repository.value,
                  project.githubInfo.flatMap(_.logo.map(_.target)),
                  project.artifactNames.map(_.value)
                )
              }

              binaryVersion match {
                case Some(_) =>
                  val result = searchEngine
                    .find(q, binaryVersion, cli, pageParams)
                    .map(page => page.items.map(p => convert(p)))
                  complete(OK, result)

                case None =>
                  val errorMessage =
                    s"something is wrong: $targetType $scalaVersion $scalaJsVersion $scalaNativeVersion $sbtVersion"
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
                  val binaryVersion = SearchApi.parseBinaryVersion(
                    targetType,
                    scalaVersion,
                    scalaJsVersion,
                    scalaNativeVersion,
                    sbtVersion
                  )
                  complete {
                    getArtifactOptions(reference, binaryVersion, artifact)
                  }
              }
            }
          }
      }
    } ~ cors() {
      AutocompletionApi.autocomplete.implementedByAsync {
        case AutocompletionApi.WithSession(request, userId) =>
          val user = userId.flatMap(session.getUser)
          autocomplete(request.searchParams(user))
      }
    }

  private def getArtifactOptions(
      projectRef: Project.Reference,
      binaryVersion: Option[BinaryVersion],
      artifact: Option[String]
  ): Future[Option[SearchApi.ArtifactOptions]] = {
    val selection = new ArtifactSelection(
      binaryVersion = binaryVersion,
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
