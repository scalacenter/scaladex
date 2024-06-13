package scaladex.core.api

import scaladex.core.model.Artifact
import scaladex.core.model.Project

trait Endpoints
    extends JsonSchemas
    with endpoints4s.algebra.Endpoints
    with endpoints4s.algebra.JsonEntitiesFromSchemas {

  private val projectPath: Path[Project.Reference] =
    (segment[String]("organization") / segment[String]("repository"))
      .xmap { case (org, repo) => Project.Reference.from(org, repo) }(ref =>
        (ref.organization.value, ref.repository.value)
      )

  private val artifactPath: Path[Artifact.MavenReference] =
    (segment[String]("groupId") / segment[String]("artifactId") / segment[String]("version"))
      .xmap { case (groupId, artifactId, version) => Artifact.MavenReference(groupId, artifactId, version) }(ref =>
        (ref.groupId, ref.artifactId, ref.version)
      )

  private val autocompletionQueryString: QueryString[AutocompletionParams] = (
    qs[String]("q", docs = Some("Main query (e.g., 'json', 'testing', etc.)")) &
      qs[Seq[String]]("topics", docs = Some("Filter on Github topics")) &
      qs[Seq[String]](
        "languages",
        docs = Some("Filter on language versions (e.g., '3', '2.13', '2.12', '2.11', 'java')")
      ) &
      qs[Seq[String]](
        "platforms",
        docs = Some("Filter on runtime platforms (e.g., 'jvm', 'sjs1', 'native0.4', 'sbt1.0')")
      ) &
      qs[Option[Boolean]]("contributingSearch").xmap(_.getOrElse(false))(Option.when(_)(true)) &
      qs[Option[String]]("you", docs = Some("internal usage")).xmap[Boolean](_.contains("✓"))(Option.when(_)("✓"))
  ).xmap((AutocompletionParams.apply _).tupled)(Function.unlift(AutocompletionParams.unapply))

  val listProjects: Endpoint[Unit, Seq[Project.Reference]] =
    endpoint(
      get(path / "api" / "projects"),
      ok(jsonResponse[Seq[Project.Reference]])
    )

  val listProjectArtifacts: Endpoint[Project.Reference, Seq[Artifact.MavenReference]] =
    endpoint(
      get(path / "api" / "projects" / projectPath / "artifacts"),
      ok(jsonResponse[Seq[Artifact.MavenReference]])
    )

  val getArtifact: Endpoint[Artifact.MavenReference, Option[ArtifactResponse]] =
    endpoint(
      get(path / "api" / "artifacts" / artifactPath),
      ok(jsonResponse[ArtifactResponse]).orNotFound()
    )

  val autocomplete: Endpoint[AutocompletionParams, Seq[AutocompletionResponse]] =
    endpoint(
      get(path / "api" / "autocomplete" /? autocompletionQueryString),
      ok(jsonResponse[Seq[AutocompletionResponse]])
    )
}
