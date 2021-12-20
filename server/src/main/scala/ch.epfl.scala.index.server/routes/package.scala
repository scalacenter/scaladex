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
import ch.epfl.scala.index.model.release.ReleaseSelection
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.DocumentationLink
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.search.SearchParams
import ch.epfl.scala.services.WebDatabase

package object routes {

  val organizationM: PathMatcher1[NewProject.Organization] =
    Segment.map(NewProject.Organization.apply)
  val repositoryM: PathMatcher1[NewProject.Repository] =
    Segment.map(NewProject.Repository.apply)
  val artifactM: PathMatcher1[NewRelease.ArtifactName] =
    Segment.map(NewRelease.ArtifactName.apply)
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
  val editForm: Directive1[NewProject.DataForm] =
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

          val dataForm = NewProject.DataForm(
            defaultStableVersion,
            defaultArtifact.map(ArtifactName.apply),
            strictVersions,
            customScalaDoc,
            documentationLinks,
            deprecated,
            contributorsWanted,
            artifactDeprecations.map(ArtifactName.apply).toSet,
            cliArtifacts.map(ArtifactName.apply).toSet,
            primaryTopic,
            beginnerIssuesLabel
          )
          Tuple1(dataForm)
      }
    )
  def getSelectedRelease(
      db: WebDatabase,
      org: NewProject.Organization,
      repo: NewProject.Repository,
      platform: Option[String],
      artifact: Option[NewRelease.ArtifactName],
      version: Option[String],
      selected: Option[String]
  )(implicit ec: ExecutionContext): Future[Option[NewRelease]] = {
    val releaseSelection = ReleaseSelection.parse(
      platform = platform,
      artifactName = artifact,
      version = version,
      selected = selected
    )
    val projectRef = NewProject.Reference(org, repo)
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
      project: NewProject,
      platform: Option[String],
      artifact: Option[NewRelease.ArtifactName],
      version: Option[String],
      selected: Option[String]
  )(implicit ec: ExecutionContext): Future[Option[NewRelease]] = {
    val releaseSelection = ReleaseSelection.parse(
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
