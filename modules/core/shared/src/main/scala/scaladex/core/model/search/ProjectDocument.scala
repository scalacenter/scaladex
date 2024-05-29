package scaladex.core.model.search

import java.time.Instant

import scaladex.core.model._

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
      header: Option[ProjectHeader],
      dependents: Long,
      formerReferences: Seq[Project.Reference]
  ): ProjectDocument = {
    val (deprecatedArtifactNames, artifactNames) =
      header.toSeq.flatMap(_.allArtifactNames).partition(project.settings.deprecatedArtifacts.contains)
    ProjectDocument(
      project.organization,
      project.repository,
      artifactNames,
      deprecatedArtifactNames,
      project.hasCli,
      project.creationDate,
      updateDate = None,
      header.toSeq.flatMap(_.latestLanguages),
      header.toSeq.flatMap(_.latestPlatforms),
      dependents,
      project.settings.category,
      formerReferences,
      project.githubInfo.map(_.toDocument)
    )
  }

}
