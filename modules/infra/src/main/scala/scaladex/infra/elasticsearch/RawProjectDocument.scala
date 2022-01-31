package scaladex.infra.elasticsearch

import java.time.Instant

import com.sksamuel.elastic4s.Indexable
import io.circe.Codec
import io.circe.Printer
import io.circe.generic.semiauto
import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Category
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.search.GithubInfoDocument
import scaladex.core.model.search.ProjectDocument

// A RawProjectDocument is a ProjectDocument where values are not yet validated.
// It can contain invalid values that will be filtered when converting to ProjectDocument.
case class RawProjectDocument(
    organization: Project.Organization,
    repository: Project.Repository,
    artifactNames: Seq[Artifact.Name],
    hasCli: Boolean,
    creationDate: Option[Instant],
    updateDate: Option[Instant],
    platformTypes: Seq[String],
    scalaVersions: Seq[String],
    scalaJsVersions: Seq[String],
    scalaNativeVersions: Seq[String],
    sbtVersions: Seq[String],
    inverseProjectDependencies: Int,
    category: Option[String],
    formerReferences: Seq[Project.Reference],
    githubInfo: Option[GithubInfoDocument]
) {
  def toProjectDocument: ProjectDocument = ProjectDocument(
    organization,
    repository,
    artifactNames,
    hasCli,
    creationDate,
    updateDate,
    platformTypes.flatMap(Platform.PlatformType.ofName).sorted,
    scalaVersions,
    scalaJsVersions.flatMap(BinaryVersion.parse).filter(Platform.ScalaJs.isValid).sorted,
    scalaNativeVersions.flatMap(BinaryVersion.parse).filter(Platform.ScalaNative.isValid).sorted,
    sbtVersions.flatMap(BinaryVersion.parse).filter(Platform.SbtPlugin.isValid).sorted,
    inverseProjectDependencies,
    category.flatMap(Category.byLabel.get),
    formerReferences,
    githubInfo
  )
}

object RawProjectDocument {
  import scaladex.infra.Codecs._
  import io.circe.syntax._
  implicit val codec: Codec[RawProjectDocument] = semiauto.deriveCodec
  implicit val indexable: Indexable[RawProjectDocument] = rawDocument => Printer.noSpaces.print(rawDocument.asJson)

  def from(project: ProjectDocument): RawProjectDocument = {
    import project._
    RawProjectDocument(
      organization,
      repository,
      artifactNames,
      hasCli,
      creationDate,
      updateDate,
      platformTypes.map(_.name),
      scalaVersions,
      scalaJsVersions.map(_.toString),
      scalaNativeVersions.map(_.toString),
      sbtVersions.map(_.toString),
      inverseProjectDependencies,
      category.map(_.label),
      formerReferences,
      githubInfo
    )
  }
}
