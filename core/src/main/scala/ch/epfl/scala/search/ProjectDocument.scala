package ch.epfl.scala.search

import java.time.Instant

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.release.BinaryVersion
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Platform.SbtPlugin
import ch.epfl.scala.index.model.release.Platform.ScalaJs
import ch.epfl.scala.index.model.release.Platform.ScalaNative
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease

// Project document indexed by the search engine
final case class ProjectDocument(
    organization: NewProject.Organization,
    repository: NewProject.Repository,
    artifactNames: Seq[NewRelease.ArtifactName],
    hasCli: Boolean,
    createdAt: Option[Instant],
    updatedAt: Option[Instant],
    platformTypes: Seq[Platform.PlatformType],
    scalaVersions: Seq[String], // scala version families TODO move to BinaryVersion
    scalaJsVersions: Seq[BinaryVersion],
    scalaNativeVersions: Seq[BinaryVersion],
    sbtVersions: Seq[BinaryVersion],
    inverseProjectDependencies: Int,
    primaryTopic: Option[String],
    githubInfo: Option[GithubInfo]
) {
  def reference: NewProject.Reference = NewProject.Reference(organization, repository)
  def id: String = reference.toString
}

object ProjectDocument {
  def apply(project: NewProject, releases: Seq[NewRelease], inverseProjectDependencies: Int): ProjectDocument = {
    import project._
    val platforms = releases.map(_.platform)
    ProjectDocument(
      organization,
      repository,
      releases.map(_.artifactName).sorted.distinct,
      hasCli,
      created,
      updatedAt = None,
      platforms.map(_.platformType).sorted.distinct,
      platforms.flatMap(_.scalaVersion).map(_.family).sorted.distinct,
      platforms.collect { case ScalaJs(_, scalaJsV) => scalaJsV }.sorted.distinct,
      platforms.collect { case ScalaNative(_, scalaNativeV) => scalaNativeV }.sorted.distinct,
      platforms.collect { case SbtPlugin(_, sbtV) => sbtV }.sorted.distinct,
      inverseProjectDependencies,
      dataForm.primaryTopic,
      githubInfo
    )
  }
}
