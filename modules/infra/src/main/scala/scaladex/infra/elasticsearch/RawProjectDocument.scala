package scaladex.infra.elasticsearch

import java.time.Instant

import scaladex.core.model.Artifact
import scaladex.core.model.Category
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.Version
import scaladex.core.model.search.GithubInfoDocument
import scaladex.core.model.search.ProjectDocument

import com.sksamuel.elastic4s.Indexable
import io.circe.Codec
import io.circe.Printer
import io.circe.generic.semiauto

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
    latestVersion: Option[String],
    dependents: Long,
    category: Option[String],
    formerReferences: Seq[Project.Reference],
    githubInfo: Option[GithubInfoDocument]
):
  def toProjectDocument: ProjectDocument = ProjectDocument(
    organization,
    repository,
    artifactNames,
    deprecatedArtifactNames,
    hasCli,
    creationDate,
    updateDate,
    languages.flatMap(Language.parse).sorted,
    platforms.flatMap(Platform.parse).sorted,
    latestVersion.map(Version.apply),
    dependents,
    category.flatMap(Category.byLabel.get),
    formerReferences,
    githubInfo
  )
end RawProjectDocument

object RawProjectDocument:
  import scaladex.infra.Codecs.given
  import io.circe.syntax.*
  given Codec[RawProjectDocument] = semiauto.deriveCodec
  given Indexable[RawProjectDocument] = rawDocument => Printer.noSpaces.print(rawDocument.asJson)

  def from(project: ProjectDocument): RawProjectDocument =
    import project.*
    RawProjectDocument(
      organization,
      repository,
      artifactNames,
      deprecatedArtifactNames,
      hasCli,
      creationDate,
      updateDate,
      languages.map(_.value),
      platforms.map(_.value),
      latestVersion.map(_.value),
      dependents,
      category.map(_.label),
      formerReferences,
      githubInfo
    )
  end from
end RawProjectDocument
