package scaladex.core.model.search

import java.time.Instant

import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Platform
import scaladex.core.model.Platform._
import scaladex.core.model.Project

// Project document indexed by the search engine
final case class ProjectDocument(
    organization: Project.Organization,
    repository: Project.Repository,
    artifactNames: Seq[Artifact.Name],
    hasCli: Boolean,
    creationDate: Option[Instant],
    updateDate: Option[Instant],
    platformTypes: Seq[Platform.PlatformType],
    scalaVersions: Seq[String], // scala version families TODO move to BinaryVersion
    scalaJsVersions: Seq[BinaryVersion],
    scalaNativeVersions: Seq[BinaryVersion],
    sbtVersions: Seq[BinaryVersion],
    inverseProjectDependencies: Int,
    primaryTopic: Option[String],
    formerReferences: Seq[Project.Reference],
    githubInfo: Option[GithubInfoDocument]
) {
  def reference: Project.Reference = Project.Reference(organization, repository)
  def id: String = reference.toString
}

object ProjectDocument {
  def apply(
      project: Project,
      releases: Seq[Artifact],
      inverseProjectDependencies: Int,
      formerReferences: Seq[Project.Reference]
  ): ProjectDocument = {
    import project._
    val platforms = releases.map(_.platform)
    ProjectDocument(
      organization,
      repository,
      releases.map(_.artifactName).sorted.distinct,
      hasCli,
      creationDate,
      updateDate = None,
      platforms.map(_.platformType).sorted.distinct,
      platforms.flatMap(_.scalaVersion).map(_.family).sorted.distinct,
      platforms.collect { case ScalaJs(_, scalaJsV) => scalaJsV }.sorted.distinct,
      platforms.collect { case ScalaNative(_, scalaNativeV) => scalaNativeV }.sorted.distinct,
      platforms.collect { case SbtPlugin(_, sbtV) => sbtV }.sorted.distinct,
      inverseProjectDependencies,
      settings.primaryTopic,
      formerReferences,
      project.githubInfo.map(_.toDocument)
    )
  }

}
