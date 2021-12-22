package ch.epfl.scala.index.server

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher1
import akka.http.scaladsl.unmarshalling.Unmarshaller
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.UserState
import ch.epfl.scala.index.model.release.ArtifactSelection
import ch.epfl.scala.index.newModel.Artifact
import ch.epfl.scala.index.newModel.Artifact.Name
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.index.newModel.Project.DocumentationLink
import ch.epfl.scala.search.SearchParams
import ch.epfl.scala.services.WebDatabase

package object routes {

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
        SearchParams(
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
  val editForm: Directive1[Project.DataForm] =
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
                  DocumentationLink.from(label, link)
              }
              .toList

          val dataForm = Project.DataForm(
            defaultStableVersion,
            defaultArtifact.map(Name.apply),
            strictVersions,
            customScalaDoc,
            documentationLinks,
            deprecated,
            contributorsWanted,
            artifactDeprecations.map(Name.apply).toSet,
            cliArtifacts.map(Name.apply).toSet,
            primaryTopic,
            beginnerIssuesLabel
          )
          Tuple1(dataForm)
      }
    )
  def getSelectedRelease(
      db: WebDatabase,
      org: Project.Organization,
      repo: Project.Repository,
      platform: Option[String],
      artifact: Option[Artifact.Name],
      version: Option[String],
      selected: Option[String]
  )(implicit ec: ExecutionContext): Future[Option[Artifact]] = {
    val releaseSelection = ArtifactSelection.parse(
      platform = platform,
      artifactName = artifact,
      version = version,
      selected = selected
    )
    val projectRef = Project.Reference(org, repo)
    for {
      project <- db.findProject(projectRef)
      releases <- db.findReleases(projectRef)
      filteredReleases = project
        .map(p => releaseSelection.filterReleases(releases, p))
        .getOrElse(Nil)
    } yield filteredReleases.headOption
  }
  def getSelectedRelease(
      db: WebDatabase,
      project: Project,
      platform: Option[String],
      artifact: Option[Artifact.Name],
      version: Option[String],
      selected: Option[String]
  )(implicit ec: ExecutionContext): Future[Option[Artifact]] = {
    val releaseSelection = ArtifactSelection.parse(
      platform = platform,
      artifactName = artifact,
      version = version,
      selected = selected
    )
    for {
      releases <- db.findReleases(project.reference)
      filteredReleases = releaseSelection.filterReleases(releases, project)
    } yield filteredReleases.headOption
  }

}
