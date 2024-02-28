package scaladex.core.api

import java.time.Instant

import scaladex.core.model.Artifact
import scaladex.core.model.Language
import scaladex.core.model.License
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.SemanticVersion

/**
 * The Json schema of the Scaladex API
 */
trait JsonSchemas extends endpoints4s.algebra.JsonSchemas {
  implicit val projectReferenceSchema: JsonSchema[Project.Reference] =
    field[String]("organization")
      .zip(field[String]("repository"))
      .xmap(Function.tupled(Project.Reference.from(_, _)))(ref => (ref.organization.value, ref.repository.value))

  implicit val mavenReferenceSchema: JsonSchema[Artifact.MavenReference] =
    field[String]("groupId")
      .zip(field[String]("artifactId"))
      .zip(field[String]("version"))
      .xmap(Function.tupled(Artifact.MavenReference.apply _))(Function.unlift(Artifact.MavenReference.unapply))

  implicit val getArtifactResponseSchema: JsonSchema[ArtifactResponse] =
    field[String]("groupId")
      .xmap(Artifact.GroupId.apply)(_.value)
      .zip(field[String]("artifactId"))
      .zip(field[String]("version").xmap(SemanticVersion.from)(_.encode))
      .zip(field[String]("artifactName").xmap(Artifact.Name.apply)(_.value))
      .zip(field[String]("project").xmap(Project.Reference.from)(_.toString))
      .zip(field[Long]("releaseDate").xmap(Instant.ofEpochMilli)(_.toEpochMilli))
      .zip(field[Seq[String]]("licenses").xmap(_.flatMap(License.get))(_.map(_.shortName)))
      .zip(field[String]("language").xmap(Language.fromLabel(_).get)(_.label))
      .zip(field[String]("platform").xmap(Platform.fromLabel(_).get)(_.label))
      .xmap {
        case (groupId, artifactId, version, artifactName, project, releaseDate, licenses, language, platform) =>
          ArtifactResponse(
            groupId,
            artifactId,
            version,
            artifactName,
            project,
            releaseDate,
            licenses,
            language,
            platform
          )
      }(Function.unlift(ArtifactResponse.unapply))

  implicit val autocompletionResponseSchema: JsonSchema[AutocompletionResponse] =
    field[String]("organization")
      .zip(field[String]("repository"))
      .zip(field[String]("description"))
      .xmap[AutocompletionResponse] {
        case (organization, repository, description) => AutocompletionResponse(organization, repository, description)
      } { autocompletionResponse =>
        (autocompletionResponse.organization, autocompletionResponse.repository, autocompletionResponse.description)
      }
}
