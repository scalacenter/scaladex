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

  val artifactMetadataEndpointParams: Path[ArtifactMetadataParams] = (segment[String](
    name = "group_id",
    docs = Some(
      "Filter the results matching the given group id only (e.g., 'org.typelevel', 'org.scala-lang', 'org.apache.spark')"
    )
  ) / segment[String](
    name = "artifact_id",
    docs = Some(
      "Filter the results matching the given artifact id only (e.g., 'cats-core_3', 'cats-core_sjs0.6_2.13')"
    )
  )).xmap(ArtifactMetadataParams.tupled)(Function.unlift(ArtifactMetadataParams.unapply))

  // Artifact endpoint definition
  val artifact: Endpoint[ArtifactParams, Page[ArtifactResponse]] =
    endpoint(
      get(path / "api" / "artifacts" /? artifactEndpointParams),
      ok(jsonResponse[Page[ArtifactResponse]])
    )

  // Artifact metadata endpoint definition
  val artifactMetadata: Endpoint[ArtifactMetadataParams, Page[ArtifactMetadataResponse]] =
    endpoint(
      get(path / "api" / "artifacts" / artifactMetadataEndpointParams),
      ok(jsonResponse[Page[ArtifactMetadataResponse]])
    )
}
