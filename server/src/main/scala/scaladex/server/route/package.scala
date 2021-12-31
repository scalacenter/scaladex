package scaladex.server

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher1
import akka.http.scaladsl.unmarshalling.Unmarshaller
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactSelection
import scaladex.core.model.Project
import scaladex.core.model.SemanticVersion
import scaladex.core.model.UserState
import scaladex.core.model.search
import scaladex.core.model.search.SearchParams
import scaladex.core.service.WebDatabase

package object route {

  val organizationM: PathMatcher1[Project.Organization] =
    Segment.map(Project.Organization.apply)
  val repositoryM: PathMatcher1[Project.Repository] =
    Segment.map(Project.Repository.apply)
  val artifactM: PathMatcher1[Artifact.Name] =
    Segment.map(Artifact.Name.apply)
  val versionM: PathMatcher1[SemanticVersion] =
    Segment.flatMap(SemanticVersion.tryParse)

  val instantUnmarshaller: Unmarshaller[String, Instant] =
    // dataRaw is in seconds
    Unmarshaller.strict[String, Instant](dateRaw => Instant.ofEpochSecond(dateRaw.toLong))

  def searchParams(user: Option[UserState]): Directive1[SearchParams] =
    parameters(
      (
        "q" ? "*",
        "page".as[Int] ? 1,
        "sort".?,
        "topics".as[String].*,
        "targetTypes".as[String].*,
        "scalaVersions".as[String].*,
        "scalaJsVersions".as[String].*,
        "scalaNativeVersions".as[String].*,
        "sbtVersions".as[String].*,
        "you".?,
        "contributingSearch".as[Boolean] ? false
      )
    ).tmap {
      case (
            q,
            page,
            sort,
            topics,
            targetTypes,
            scalaVersions,
            scalaJsVersions,
            scalaNativeVersions,
            sbtVersions,
            you,
            contributingSearch
          ) =>
        val userRepos = you
          .flatMap(_ => user.map(_.repos))
          .getOrElse(Set())
        search.SearchParams(
          q,
          page,
          sort,
          userRepos,
          topics = topics.toList,
          targetTypes = targetTypes.toList,
          scalaVersions = scalaVersions.toList,
          scalaJsVersions = scalaJsVersions.toList,
          scalaNativeVersions = scalaNativeVersions.toList,
          sbtVersions = sbtVersions.toList,
          contributingSearch = contributingSearch
        )
    }

  // TODO remove all unused parameters
  val editForm: Directive1[Project.Settings] =
    formFieldSeq.tflatMap(fields =>
      formFields(
        (
          "contributorsWanted".as[Boolean] ? false,
          "defaultArtifact".?,
          "defaultStableVersion".as[Boolean] ? false,
          "strictVersions".as[Boolean] ? false,
          "deprecated".as[Boolean] ? false,
          "artifactDeprecations".as[String].*,
          "cliArtifacts".as[String].*,
          "customScalaDoc".?,
          "primaryTopic".?,
          "beginnerIssuesLabel".?,
          "beginnerIssues".?,
          "selectedBeginnerIssues".as[String].*,
          "chatroom".?,
          "contributingGuide".?,
          "codeOfConduct".?
        )
      ).tmap {
        case (
              contributorsWanted,
              defaultArtifact,
              defaultStableVersion,
              strictVersions,
              deprecated,
              artifactDeprecations,
              cliArtifacts,
              customScalaDoc,
              primaryTopic,
              beginnerIssuesLabel,
              beginnerIssues,
              selectedBeginnerIssues,
              chatroom,
              contributingGuide,
              codeOfConduct
            ) =>
          val documentationLinks =
            fields._1
              .filter { case (key, _) => key.startsWith("documentationLinks") }
              .groupBy {
                case (key, _) =>
                  key
                    .drop("documentationLinks[".length)
                    .takeWhile(_ != ']')
              }
              .values
              .map {
                case Vector((a, b), (_, d)) =>
                  if (a.contains("label")) (b, d)
                  else (d, b)
              }
              .flatMap {
                case (label, link) =>
                  Project.DocumentationLink.from(label, link)
              }
              .toList

          val settings = Project.Settings(
            defaultStableVersion,
            defaultArtifact.map(Artifact.Name.apply),
            strictVersions,
            customScalaDoc,
            documentationLinks,
            deprecated,
            contributorsWanted,
            artifactDeprecations.map(Artifact.Name.apply).toSet,
            cliArtifacts.map(Artifact.Name.apply).toSet,
            primaryTopic,
            beginnerIssuesLabel
          )
          Tuple1(settings)
      }
    )
  def getSelectedArtifact(
      database: WebDatabase,
      org: Project.Organization,
      repo: Project.Repository,
      platform: Option[String],
      artifact: Option[Artifact.Name],
      version: Option[String],
      selected: Option[String]
  )(implicit ec: ExecutionContext): Future[Option[Artifact]] = {
    val artifactSelection = ArtifactSelection.parse(
      platform = platform,
      artifactName = artifact,
      version = version,
      selected = selected
    )
    val projectRef = Project.Reference(org, repo)
    for {
      project <- database.getProject(projectRef)
      artifacts <- database.getArtifacts(projectRef)
      filteredArtifacts = project
        .map(p => artifactSelection.filterArtifacts(artifacts, p))
        .getOrElse(Nil)
    } yield filteredArtifacts.headOption
  }
  def getSelectedArtifact(
      database: WebDatabase,
      project: Project,
      platform: Option[String],
      artifact: Option[Artifact.Name],
      version: Option[String],
      selected: Option[String]
  )(implicit ec: ExecutionContext): Future[Option[Artifact]] = {
    val artifactSelection = ArtifactSelection.parse(
      platform = platform,
      artifactName = artifact,
      version = version,
      selected = selected
    )
    for {
      artifacts <- database.getArtifacts(project.reference)
      filteredArtifacts = artifactSelection.filterArtifacts(artifacts, project)
    } yield filteredArtifacts.headOption
  }

}
