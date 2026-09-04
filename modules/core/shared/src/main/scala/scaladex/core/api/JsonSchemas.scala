package scaladex.core.api

import java.time.Instant

import scaladex.core.model.*
import scaladex.core.model.search.Page
import scaladex.core.model.search.Pagination

import endpoints4s.Invalid
import endpoints4s.Valid
import endpoints4s.Validated

/** The Json schema of the Scaladex API
  */
trait JsonSchemas extends endpoints4s.algebra.JsonSchemas:
  private val stringJson = stringJsonSchema(None)

  given JsonSchema[Project.Organization] = stringJson.xmap(Project.Organization(_))(_.value)
  given JsonSchema[Project.Repository] = stringJson.xmap(Project.Repository(_))(_.value)
  given JsonSchema[Artifact.GroupId] = stringJson.xmap(Artifact.GroupId(_))(_.value)
  given JsonSchema[Artifact.ArtifactId] = stringJson.xmap(Artifact.ArtifactId(_))(_.value)
  given JsonSchema[Version] = stringJson.xmap(Version(_))(_.value)
  given JsonSchema[Url] = stringJson.xmap(Url(_))(_.target)
  given JsonSchema[License] = stringJson
    .xmapPartial(l => Validated.fromOption(License.get(l))(s"Unknown license $l"))(_.shortName)
  given JsonSchema[Artifact.Name] = stringJson.xmap(Artifact.Name(_))(_.value)
  given JsonSchema[Category] = stringJson
    .xmapPartial(c => Validated.fromOption(Category.byLabel.get(c))(s"Unknown category $c"))(_.label)
  given JsonSchema[BinaryVersion] = stringJson
    .xmapPartial(l => Validated.fromOption(BinaryVersion.parse(l))(s"Unknown binary version $l"))(_.value)
  given JsonSchema[Language] = stringJson
    .xmapPartial(l => Validated.fromOption(Language.parse(l))(s"Unknown language $l"))(_.value)
  given JsonSchema[Platform] = stringJson
    .xmapPartial(l => Validated.fromOption(Platform.parse(l))(s"Unknown platform $l"))(_.value)

  given JsonSchema[Project.Reference] =
    field[Project.Organization]("organization")
      .zip(field[Project.Repository]("repository"))
      .xmap { case (org, repo) => Project.Reference(org, repo) }(ref => (ref.organization, ref.repository))

  given given_JsonSchema_Artifact_Reference: JsonSchema[Artifact.Reference] =
    field[Artifact.GroupId]("groupId")
      .zip(field[Artifact.ArtifactId]("artifactId"))
      .zip(field[Version]("version"))
      .xmap(Artifact.Reference.apply)(Tuple.fromProductTyped)

  given JsonSchema[DocumentationPattern] =
    field[String]("label")
      .zip(field[String]("pattern"))
      .xmap { case (label, pattern) => DocumentationPattern(label, pattern) }(p => (p.label, p.pattern))

  given JsonSchema[ProjectResponse] =
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
      .xmap(ProjectResponse.apply)(Tuple.fromProductTyped)

  given JsonSchema[ArtifactResponse] =
    field[Artifact.GroupId]("groupId")
      .zip(field[Artifact.ArtifactId]("artifactId"))
      .zip(field[Version]("version"))
      .zip(field[Artifact.Name]("name"))
      .zip(field[BinaryVersion]("binaryVersion"))
      .zip(field[Language]("language"))
      .zip(field[Platform]("platform"))
      .zip(field[Project.Reference]("project"))
      .zip(field[Instant]("releaseDate"))
      .zip(field[Seq[License]]("licenses"))
      .xmap(ArtifactResponse.apply)(Tuple.fromProductTyped)

  given JsonSchema[ArtifactDependency.Scope] =
    stringJson.xmap(ArtifactDependency.Scope(_))(_.value)

  given JsonSchema[ProjectDependency] =
    field[Project.Reference]("source")
      .zip(field[Version]("sourceVersion"))
      .zip(field[Project.Reference]("target"))
      .zip(field[Version]("targetVersion"))
      .zip(field[ArtifactDependency.Scope]("scope"))
      .xmap(ProjectDependency.apply)(Tuple.fromProductTyped)

  given JsonSchema[Pagination] =
    field[Int]("current")
      .zip(field[Int]("pageCount"))
      .zip(field[Long]("totalSize"))
      .xmap(Pagination.apply)(Tuple.fromProductTyped)

  given pageJsonSchema[A](using JsonSchema[A]): JsonSchema[Page[A]] =
    field[Pagination]("pagination")
      .zip(field[Seq[A]]("items"))
      .xmap { case (pagination, items) => Page(pagination, items) }(page => (page.pagination, page.items))

  given JsonSchema[Project.Settings] =
    field[Boolean]("preferStableVersion")
      .zip(optField[Artifact.Name]("defaultArtifact"))
      .zip(optField[String]("customScalaDoc"))
      .zip(field[Seq[DocumentationPattern]]("documentationLinks"))
      .zip(field[Boolean]("contributorsWanted"))
      .zip(field[Set[Artifact.Name]]("deprecatedArtifacts"))
      .zip(field[Set[Artifact.Name]]("cliArtifacts"))
      .zip(optField[Category]("category"))
      .zip(optField[String]("chatroom"))
      .xmap(Project.Settings.apply)(Tuple.fromProductTyped)

  given JsonSchema[ProjectSettingsPatch] =
    optField[Boolean]("preferStableVersion")
      .zip(optField[String]("defaultArtifact"))
      .zip(optField[String]("customScalaDoc"))
      .zip(optField[Seq[DocumentationPattern]]("documentationLinks"))
      .zip(optField[Boolean]("contributorsWanted"))
      .zip(optField[Seq[Artifact.Name]]("deprecatedArtifacts"))
      .zip(optField[Seq[Artifact.Name]]("cliArtifacts"))
      .zip(optField[String]("category"))
      .zip(optField[String]("chatroom"))
      .xmapPartial { tuple =>
        val patch = ProjectSettingsPatch.apply.tupled(tuple)
        ProjectSettingsPatch.invalidCategory(patch.category) match
          case Some(label) => Invalid(s"Unknown category $label")
          case None => Valid(patch)
      }(Tuple.fromProductTyped)

  given JsonSchema[UserResponse] =
    field[String]("login")
      .zip(optField[String]("name"))
      .zip(field[String]("avatarUrl"))
      .zip(field[Boolean]("isAdmin"))
      .zip(field[Seq[Project.Organization]]("organizations"))
      .zip(field[Seq[Project.Reference]]("repositories"))
      .xmap(UserResponse.apply)(Tuple.fromProductTyped)

  given JsonSchema[SearchResult] =
    field[Project.Organization]("organization")
      .zip(field[Project.Repository]("repository"))
      .zip(optField[String]("description"))
      .zip(optField[Int]("stars"))
      .zip(optField[Int]("forks"))
      .zip(field[Seq[String]]("topics"))
      .zip(field[Seq[Language]]("languages"))
      .zip(field[Seq[Platform]]("platforms"))
      .zip(optField[Version]("latestVersion"))
      .zip(field[Long]("dependents"))
      .zip(optField[String]("category"))
      .xmap(SearchResult.apply)(Tuple.fromProductTyped)

  given JsonSchema[AutocompletionResponse] =
    field[String]("organization")
      .zip(field[String]("repository"))
      .zip(field[String]("description"))
      .xmap[AutocompletionResponse] {
        case (organization, repository, description) => AutocompletionResponse(organization, repository, description)
      } { autocompletionResponse =>
        (autocompletionResponse.organization, autocompletionResponse.repository, autocompletionResponse.description)
      }
end JsonSchemas
