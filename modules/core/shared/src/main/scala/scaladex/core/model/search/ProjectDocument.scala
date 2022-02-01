package scaladex.core.model.search

import java.time.Instant

import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Category
import scaladex.core.model.Platform
import scaladex.core.model.Platform._
import scaladex.core.model.Project
import scaladex.core.model.ScalaVersion

// Project document indexed by the search engine
final case class ProjectDocument(
    organization: Project.Organization,
    repository: Project.Repository,
    artifactNames: Seq[Artifact.Name],
    hasCli: Boolean,
    creationDate: Option[Instant],
    updateDate: Option[Instant],
    platformTypes: Seq[Platform.PlatformType],
    scalaVersions: Seq[ScalaVersion],
    scalaJsVersions: Seq[BinaryVersion],
    scalaNativeVersions: Seq[BinaryVersion],
    sbtVersions: Seq[BinaryVersion],
    inverseProjectDependencies: Int,
    category: Option[Category],
    formerReferences: Seq[Project.Reference],
    githubInfo: Option[GithubInfoDocument]
) {
  def reference: Project.Reference = Project.Reference(organization, repository)
  def id: String = reference.toString
}

object ProjectDocument {
  def apply(
      project: Project,
      artifacts: Seq[Artifact],
      inverseProjectDependencies: Int,
      formerReferences: Seq[Project.Reference]
  ): ProjectDocument = {
    import project._
    val platforms = artifacts.map(_.platform)
    ProjectDocument(
      organization,
      repository,
      artifacts.map(_.artifactName).sorted.distinct,
      hasCli,
      creationDate,
      updateDate = None,
      platforms.map(_.platformType).sorted.distinct,
      platforms.flatMap(_.scalaVersion).sorted.distinct,
      platforms.collect { case ScalaJs(_, scalaJsV) => scalaJsV }.sorted.distinct,
      platforms.collect { case ScalaNative(_, scalaNativeV) => scalaNativeV }.sorted.distinct,
      platforms.collect { case SbtPlugin(_, sbtV) => sbtV }.sorted.distinct,
      inverseProjectDependencies,
      settings.category,
      formerReferences,
      project.githubInfo.map(_.toDocument)
    )
  }

}
