package ch.epfl.scala.index.search

import java.time.Instant

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.release.BinaryVersion
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.search.ProjectDocument
import com.sksamuel.elastic4s.Indexable
import io.circe.Codec
import io.circe.Printer
import io.circe.generic.semiauto

// A RawProjectDocument is a ProjectDocument where values are not yet validated.
// It can contain invalid values that will be filtered when converting to ProjectDocument.
case class RawProjectDocument(
    organization: Project.Organization,
    repository: Project.Repository,
    artifactNames: Seq[NewRelease.ArtifactName],
    hasCli: Boolean,
    createdAt: Option[Instant],
    updatedAt: Option[Instant],
    platformTypes: Seq[String],
    scalaVersions: Seq[String],
    scalaJsVersions: Seq[String],
    scalaNativeVersions: Seq[String],
    sbtVersions: Seq[String],
    inverseProjectDependencies: Int,
    primaryTopic: Option[String],
    githubInfo: Option[GithubInfo]
) {
  def toProjectDocument: ProjectDocument = ProjectDocument(
    organization,
    repository,
    artifactNames,
    hasCli,
    createdAt,
    updatedAt,
    platformTypes.flatMap(Platform.PlatformType.ofName).sorted,
    scalaVersions,
    scalaJsVersions.flatMap(BinaryVersion.parse).filter(Platform.ScalaJs.isValid).sorted,
    scalaNativeVersions.flatMap(BinaryVersion.parse).filter(Platform.ScalaNative.isValid).sorted,
    sbtVersions.flatMap(BinaryVersion.parse).filter(Platform.SbtPlugin.isValid).sorted,
    inverseProjectDependencies,
    primaryTopic,
    githubInfo
  )
}

object RawProjectDocument {
  import ch.epfl.scala.utils.Codecs._
  import io.circe.syntax._
  implicit val codec: Codec[RawProjectDocument] = semiauto.deriveCodec[RawProjectDocument]
  implicit val indexable: Indexable[RawProjectDocument] = rawDocument => Printer.noSpaces.print(rawDocument.asJson)

  def from(project: ProjectDocument): RawProjectDocument = {
    import project._
    RawProjectDocument(
      organization,
      repository,
      artifactNames,
      hasCli,
      createdAt,
      updatedAt,
      platformTypes.map(_.name),
      scalaVersions,
      scalaJsVersions.map(_.toString),
      scalaNativeVersions.map(_.toString),
      sbtVersions.map(_.toString),
      inverseProjectDependencies,
      primaryTopic,
      githubInfo
    )
  }
}
