package scaladex.server.route.api

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.*
import scaladex.core.model.search.PageParams
import scaladex.core.model.search.ProjectDocument
import scaladex.core.service.SearchEngine
import scaladex.core.service.WebDatabase

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.*
import io.circe.generic.semiauto
import org.apache.pekko.http.cors.scaladsl.CorsDirectives.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route

object OldSearchApi:
  given Codec[OldSearchApi.Project] = semiauto.deriveCodec
  given Codec[ArtifactOptions] = semiauto.deriveCodec

  case class Project(
      organization: String,
      repository: String,
      logo: Option[String],
      artifacts: Seq[String],
      deprecatedArtifacts: Seq[String]
  )

  case class ArtifactOptions(
      artifacts: Seq[String],
      deprecatedArtifacts: Seq[String],
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
  ): Option[BinaryVersion] =
    val binaryVersion = (targetType, scalaVersion, scalaJsVersion, scalaNativeVersion, sbtVersion) match
      case (Some("JVM"), Some(sv), _, _, _) =>
        Version.parseSemantically(sv).map(sv => BinaryVersion(Jvm, Scala(sv)))

      case (Some("JS"), Some(sv), Some(jsv), _, _) =>
        for
          sv <- Version.parseSemantically(sv)
          jsv <- Version.parseSemantically(jsv)
        yield BinaryVersion(ScalaJs(jsv), Scala(sv))

      case (Some("NATIVE"), Some(sv), _, Some(snv), _) =>
        for
          sv <- Version.parseSemantically(sv)
          snv <- Version.parseSemantically(snv)
        yield BinaryVersion(ScalaNative(snv), Scala(sv))

      case (Some("SBT"), Some(sv), _, _, Some(sbtv)) =>
        for
          sv <- Version.parseSemantically(sv)
          sbtv <- Version.parseSemantically(sbtv)
        yield BinaryVersion(SbtPlugin(sbtv), Scala(sv))

      case (Some("JVM"), None, None, None, None) => Some(BinaryVersion(Jvm, Java))
      case _ => None
    binaryVersion.filter(_.isValid)
  end parseBinaryVersion
end OldSearchApi

class OldSearchApi(searchEngine: SearchEngine, database: WebDatabase)(using ExecutionContext)
    extends FailFastCirceSupport:
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
              val binaryVersion = OldSearchApi.parseBinaryVersion(
                Some(targetType),
                scalaVersion,
                scalaJsVersion,
                scalaNativeVersion,
                sbtVersion
              )
              val pageParams = PageParams(page, total)

              def convert(project: ProjectDocument): OldSearchApi.Project =
                OldSearchApi.Project(
                  project.organization.value,
                  project.repository.value,
                  project.githubInfo.flatMap(_.logo.map(_.target)),
                  project.artifactNames.map(_.value),
                  project.deprecatedArtifactNames.map(_.value)
                )

              binaryVersion match
                case Some(_) =>
                  val result = searchEngine
                    .find(q, binaryVersion, cli, pageParams)
                    .map(page => page.items.map(p => convert(p)))
                  complete(OK, result)

                case None =>
                  val errorMessage =
                    s"something is wrong: $targetType $scalaVersion $scalaJsVersion $scalaNativeVersion $sbtVersion"
                  complete(BadRequest, errorMessage)
              end match
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
                  val binaryVersion = OldSearchApi.parseBinaryVersion(
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
    }

  private def getArtifactOptions(
      projectRef: Project.Reference,
      binaryVersion: Option[BinaryVersion],
      artifact: Option[String]
  ): Future[Option[OldSearchApi.ArtifactOptions]] =
    val selection =
      new ArtifactSelection(binaryVersion = binaryVersion, artifactNames = artifact.map(Artifact.Name.apply))
    for
      projectOpt <- database.getProject(projectRef)
      stableOnly = projectOpt.map(_.settings.preferStableVersion).getOrElse(false)
      artifacts <- database.getProjectArtifactRefs(projectRef, stableOnly)
    yield for
      project <- projectOpt
      filteredArtifacts = selection.filterArtifacts(artifacts, project)
      selected <- filteredArtifacts.headOption
    yield
      val (deprecatedArtifacts, artifacts) = filteredArtifacts
        .map(_.name)
        .distinct
        .partition(project.settings.deprecatedArtifacts.contains)
      // Sort semantic versions by descending order
      val versions = filteredArtifacts.map(_.version).distinct.sorted(Ordering[Version].reverse)
      OldSearchApi.ArtifactOptions(
        artifacts = artifacts.map(_.value),
        deprecatedArtifacts = deprecatedArtifacts.map(_.value),
        versions.map(_.toString),
        selected.groupId.value,
        selected.artifactId.value,
        selected.version.value
      )
    end for
  end getArtifactOptions
end OldSearchApi
