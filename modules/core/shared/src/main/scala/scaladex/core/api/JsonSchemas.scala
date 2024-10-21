package scaladex.core.api

import java.time.Instant

import endpoints4s.Validated
import scaladex.core.model._

/**
 * The Json schema of the Scaladex API
 */
trait JsonSchemas extends endpoints4s.algebra.JsonSchemas {
  private val stringJson = stringJsonSchema(None)

  implicit val organizationSchema: JsonSchema[Project.Organization] = stringJson.xmap(Project.Organization(_))(_.value)
  implicit val repositorySchema: JsonSchema[Project.Repository] = stringJson.xmap(Project.Repository(_))(_.value)
  implicit val groupIdSchema: JsonSchema[Artifact.GroupId] = stringJson.xmap(Artifact.GroupId(_))(_.value)
  implicit val artifactIdSchema: JsonSchema[Artifact.ArtifactId] = stringJson.xmap(Artifact.ArtifactId(_))(_.value)
  implicit val versionSchema: JsonSchema[SemanticVersion] =
    stringJson
      .xmapPartial(v => Validated.fromOption(SemanticVersion.parse(v))(s"Cannot parse $v as version"))(_.value)
  implicit val urlSchema: JsonSchema[Url] = stringJson.xmap(Url(_))(_.target)
  implicit val licenseSchema: JsonSchema[License] = stringJson
    .xmapPartial(l => Validated.fromOption(License.get(l))(s"Unknown license $l"))(_.shortName)
  implicit val artifactNameSchema: JsonSchema[Artifact.Name] = stringJson.xmap(Artifact.Name(_))(_.value)
  implicit val categorySchema: JsonSchema[Category] = stringJson
    .xmapPartial(c => Validated.fromOption(Category.byLabel.get(c))(s"Unknown category $c"))(_.label)
  implicit val binaryVersionSchema: JsonSchema[BinaryVersion] = stringJson
    .xmapPartial(l => Validated.fromOption(BinaryVersion.parse(l))(s"Unknown binary version $l"))(_.value)
  implicit val language: JsonSchema[Language] = stringJson
    .xmapPartial(l => Validated.fromOption(Language.parse(l))(s"Unknown language $l"))(_.value)
  implicit val platform: JsonSchema[Platform] = stringJson
    .xmapPartial(l => Validated.fromOption(Platform.parse(l))(s"Unknown platform $l"))(_.value)

  implicit val projectReferenceSchema: JsonSchema[Project.Reference] =
    field[Project.Organization]("organization")
      .zip(field[Project.Repository]("repository"))
      .xmap { case (org, repo) => Project.Reference(org, repo) }(ref => (ref.organization, ref.repository))

  implicit val artifactReferenceSchema: JsonSchema[Artifact.Reference] =
    field[Artifact.GroupId]("groupId")
      .zip(field[Artifact.ArtifactId]("artifactId"))
      .zip(field[SemanticVersion]("version"))
      .xmap((Artifact.Reference.apply _).tupled)(Function.unlift(Artifact.Reference.unapply))

  implicit val documentationPatternSchema: JsonSchema[DocumentationPattern] =
    field[String]("label")
      .zip(field[String]("pattern"))
      .xmap { case (label, pattern) => DocumentationPattern(label, pattern) }(p => (p.label, p.pattern))

  implicit val projectResponseSchema: JsonSchema[ProjectResponse] =
    field[Project.Organization]("organization")
      .zip(field[Project.Repository]("repository"))
      .zip(optField[Url]("homepage"))
      .zip(optField[String]("description"))
      .zip(optField[Url]("logo"))
      .zip(optField[Int]("stars"))
      .zip(optField[Int]("forks"))
      .zip(optField[Int]("issues"))
      .zip(field[Set[String]]("topics"))
      .zip(optField[Url]("contributingGuide"))
      .zip(optField[Url]("codeOfConduct"))
      .zip(optField[License]("license"))
      .zip(optField[Artifact.Name]("defaultArtifact"))
      .zip(optField[String]("customScalaDoc"))
      .zip(field[Seq[DocumentationPattern]]("documentationLinks"))
      .zip(field[Boolean]("contributorsWanted"))
      .zip(field[Set[Artifact.Name]]("cliArtifacts"))
      .zip(optField[Category]("category"))
      .zip(optField[String]("chatroom"))
      .xmap((ProjectResponse.apply _).tupled)(Function.unlift(ProjectResponse.unapply))

  implicit val artifactResponseSchema: JsonSchema[ArtifactResponse] =
    field[Artifact.GroupId]("groupId")
      .zip(field[Artifact.ArtifactId]("artifactId"))
      .zip(field[SemanticVersion]("version"))
      .zip(field[Artifact.Name]("name"))
      .zip(field[BinaryVersion]("binaryVersion"))
      .zip(field[Language]("language"))
      .zip(field[Platform]("platform"))
      .zip(field[Project.Reference]("project"))
      .zip(field[Instant]("releaseDate"))
      .zip(field[Seq[License]]("licenses"))
      .xmap((ArtifactResponse.apply _).tupled)(Function.unlift(ArtifactResponse.unapply))

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
