package scaladex.core.model.search

import java.time.Instant

import scaladex.core.model.Artifact
import scaladex.core.model.Category
import scaladex.core.model.Language
import scaladex.core.model.MillPlugin
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.SbtPlugin
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative

// Project document indexed by the search engine
final case class ProjectDocument(
    organization: Project.Organization,
    repository: Project.Repository,
    artifactNames: Seq[Artifact.Name],
    deprecatedArtifactNames: Seq[Artifact.Name],
    hasCli: Boolean,
    creationDate: Option[Instant],
    updateDate: Option[Instant],
    languages: Seq[Language],
    platforms: Seq[Platform],
    dependents: Long,
    category: Option[Category],
    formerReferences: Seq[Project.Reference],
    githubInfo: Option[GithubInfoDocument]
) {
  def reference: Project.Reference = Project.Reference(organization, repository)
  def id: String = reference.toString
  def scalaVersions: Seq[Scala] = languages.collect { case v: Scala => v }
  def scalaJsVersions: Seq[ScalaJs] = platforms.collect { case v: ScalaJs => v }
  def scalaNativeVersions: Seq[ScalaNative] = platforms.collect { case v: ScalaNative => v }
  def sbtVersions: Seq[SbtPlugin] = platforms.collect { case v: SbtPlugin => v }
  def millVersions: Seq[MillPlugin] = platforms.collect { case v: MillPlugin => v }
}

object ProjectDocument {
  def default(reference: Project.Reference): ProjectDocument =
    ProjectDocument(
      reference.organization,
      reference.repository,
      Seq.empty,
      Seq.empty,
      false,
      None,
      None,
      Seq.empty,
      Seq.empty,
      0,
      None,
      Seq.empty,
      None
    )

  def apply(
      project: Project,
      artifacts: Seq[Artifact],
      dependents: Long,
      formerReferences: Seq[Project.Reference]
  ): ProjectDocument = {
    val (deprecatedArtifactNames, artifactNames) =
      artifacts
        .map(_.artifactName)
        .distinct
        .sorted
        .partition(project.settings.deprecatedArtifacts.contains)
    import project._
    ProjectDocument(
      organization,
      repository,
      artifactNames,
      deprecatedArtifactNames,
      hasCli,
      creationDate,
      updateDate = None,
      artifacts.map(_.binaryVersion.language).distinct.sorted,
      artifacts.map(_.binaryVersion.platform).distinct.sorted,
      dependents,
      settings.category,
      formerReferences,
      project.githubInfo.map(_.toDocument)
    )
  }

}
