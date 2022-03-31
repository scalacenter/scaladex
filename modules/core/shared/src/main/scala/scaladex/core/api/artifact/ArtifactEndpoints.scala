package scaladex.core.api.artifact

import endpoints4s.algebra.Endpoints
import endpoints4s.algebra.JsonEntitiesFromSchemas

trait ArtifactEndpoints extends Endpoints with JsonEntitiesFromSchemas {

  implicit val response: JsonSchema[ArtifactResponse] =
    field[String]("groupId")
      .zip(field[String]("artifactId"))
      .xmap[ArtifactResponse] { case (groupId, artifactId) => ArtifactResponse(groupId, artifactId) } {
        artifactResponse => (artifactResponse.groupId, artifactResponse.artifactId)
      }

  val params: QueryString[ArtifactParams] = (qs[Option[String]](
    name = "language",
    docs = Some(
      "Filter the results matching the given language version only (e.g., '3', '2.13', '2.12', '2.11', 'java')"
    )
  ) & qs[Option[String]](
    name = "platform",
    docs = Some("Filter the results matching the given platforms only (e.g., 'jvm', 'sjs1', 'native0.4', 'sbt1.0')")
  )).xmap((ArtifactParams.apply _).tupled)(Function.unlift(ArtifactParams.unapply))

  // Artifact endpoint definition
  val artifact: Endpoint[ArtifactParams, Seq[ArtifactResponse]] =
    endpoint(
      get(path / "api" / "artifact" /? params),
      ok(jsonResponse[Seq[ArtifactResponse]])
    )
}
