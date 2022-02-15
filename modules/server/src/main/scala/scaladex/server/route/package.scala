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

  val organizationM: PathMatcher1[Project.Organization] = Segment.map(Project.Organization.apply)
  val repositoryM: PathMatcher1[Project.Repository] = Segment.map(Project.Repository.apply)
  val artifactM: PathMatcher1[Artifact.Name] = Segment.map(Artifact.Name.apply)
  val versionM: PathMatcher1[SemanticVersion] = Segment.flatMap(SemanticVersion.parse)

  val instantUnmarshaller: Unmarshaller[String, Instant] =
    // dataRaw is in seconds
    Unmarshaller.strict[String, Instant](dateRaw => Instant.ofEpochSecond(dateRaw.toLong))

  def searchParams(user: Option[UserState]): Directive1[SearchParams] =
    parameters(
      "q" ? "*",
      "page".as[Int] ? 1,
      "sort".?,
      "topics".as[String].*,
      "languages".as[String].*,
      "platforms".as[String].*,
      "you".?,
      "contributingSearch".as[Boolean] ? false
    ).tmap {
      case (q, page, sort, topics, languages, platforms, you, contributingSearch) =>
        val userRepos = you.flatMap(_ => user.map(_.repos)).getOrElse(Set())
        search.SearchParams(
          q,
          page,
          sort,
          userRepos,
          topics = topics.toSeq,
          languages = languages.toSeq,
          platforms = platforms.toSeq,
          contributingSearch = contributingSearch
        )
    }

  def getSelectedArtifact(
      database: WebDatabase,
      org: Project.Organization,
      repo: Project.Repository,
      binaryVersion: Option[String],
      artifact: Option[Artifact.Name],
      version: Option[String],
      selected: Option[String]
  )(implicit ec: ExecutionContext): Future[Option[Artifact]] = {
    val artifactSelection = ArtifactSelection.parse(
      binaryVersion = binaryVersion,
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
      binaryVersion: Option[String],
      artifact: Option[Artifact.Name],
      version: Option[String],
      selected: Option[String]
  )(implicit ec: ExecutionContext): Future[Option[Artifact]] = {
    val artifactSelection = ArtifactSelection.parse(
      binaryVersion = binaryVersion,
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
