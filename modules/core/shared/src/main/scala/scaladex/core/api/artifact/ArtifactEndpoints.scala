package scaladex.core.api.artifact

import scaladex.core.model.search.Page

trait ArtifactEndpoints
    extends ArtifactEndpointSchema
    with endpoints4s.algebra.Endpoints
    with endpoints4s.algebra.JsonEntitiesFromSchemas {

  val artifactEndpointParams: QueryString[ArtifactParams] = (qs[Option[String]](
    name = "language",
    docs = Some(
      "Filter the results matching the given language version only (e.g., '3', '2.13', '2.12', '2.11', 'java')"
    )
  ) & qs[Option[String]](
    name = "platform",
    docs = Some("Filter the results matching the given platform only (e.g., 'jvm', 'sjs1', 'native0.4', 'sbt1.0')")
  )).xmap((ArtifactParams.apply _).tupled)(Function.unlift(ArtifactParams.unapply))

  // Artifact endpoint definition
  val artifact: Endpoint[ArtifactParams, Page[ArtifactResponse]] =
    endpoint(
      get(path / "api" / "artifacts" /? artifactEndpointParams),
      ok(jsonResponse[Page[ArtifactResponse]])
    )
}
