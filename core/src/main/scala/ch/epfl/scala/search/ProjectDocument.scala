package ch.epfl.scala.search

import java.time.Instant

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.release.BinaryVersion
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Platform.SbtPlugin
import ch.epfl.scala.index.model.release.Platform.ScalaJs
import ch.epfl.scala.index.model.release.Platform.ScalaNative
import ch.epfl.scala.index.newModel.Artifact
import ch.epfl.scala.index.newModel.Project

// Project document indexed by the search engine
final case class ProjectDocument(
    organization: Project.Organization,
    repository: Project.Repository,
    artifactNames: Seq[Artifact.Name],
    hasCli: Boolean,
    creationDate: Option[Instant],
    update: Option[Instant],
    platformTypes: Seq[Platform.PlatformType],
    scalaVersions: Seq[String], // scala version families TODO move to BinaryVersion
    scalaJsVersions: Seq[BinaryVersion],
    scalaNativeVersions: Seq[BinaryVersion],
    sbtVersions: Seq[BinaryVersion],
    inverseProjectDependencies: Int,
    primaryTopic: Option[String],
    githubInfo: Option[GithubInfo],
    formerReferences: Seq[Project.Reference]
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
      update = None,
      platforms.map(_.platformType).sorted.distinct,
      platforms.flatMap(_.scalaVersion).map(_.family).sorted.distinct,
      platforms.collect { case ScalaJs(_, scalaJsV) => scalaJsV }.sorted.distinct,
      platforms.collect { case ScalaNative(_, scalaNativeV) => scalaNativeV }.sorted.distinct,
      platforms.collect { case SbtPlugin(_, sbtV) => sbtV }.sorted.distinct,
      inverseProjectDependencies,
      dataForm.primaryTopic,
      githubInfo,
      formerReferences
    )
  }
}
