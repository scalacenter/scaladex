package scaladex.infra.elasticsearch

import java.time.Instant

import com.sksamuel.elastic4s.Indexable
import io.circe.Codec
import io.circe.Printer
import io.circe.generic.semiauto
import scaladex.core.model.Artifact
import scaladex.core.model.Category
import scaladex.core.model.Language
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
    deprecatedArtifactNames: Seq[Artifact.Name],
    hasCli: Boolean,
    creationDate: Option[Instant],
    updateDate: Option[Instant],
    languages: Seq[String],
    platforms: Seq[String],
    dependents: Long,
    category: Option[String],
    formerReferences: Seq[Project.Reference],
    githubInfo: Option[GithubInfoDocument]
) {
  def toProjectDocument: ProjectDocument = ProjectDocument(
    organization,
    repository,
    artifactNames,
    deprecatedArtifactNames,
    hasCli,
    creationDate,
    updateDate,
    languages.flatMap(Language.fromLabel).sorted,
    platforms.flatMap(Platform.fromLabel).sorted,
    dependents,
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
      deprecatedArtifactNames,
      hasCli,
      creationDate,
      updateDate,
      languages.map(_.label),
      platforms.map(_.label),
      dependents,
      category.map(_.label),
      formerReferences,
      githubInfo
    )
  }
}
