package scaladex.core.api.artifact

import endpoints4s.algebra.Endpoints
import scaladex.core.api.PaginationSchema
import scaladex.core.model.search.Page
import scaladex.core.model.search.Pagination

trait ArtifactEndpoints extends Endpoints with PaginationSchema {

  implicit val artifactResponseSchema: JsonSchema[ArtifactResponse] =
    field[String]("groupId")
      .zip(field[String]("artifactId"))
      .xmap[ArtifactResponse] { case (groupId, artifactId) => ArtifactResponse(groupId, artifactId) } {
        artifactResponse => (artifactResponse.groupId, artifactResponse.artifactId)
      }

  implicit val artifactEndpointResponse: JsonSchema[Page[ArtifactResponse]] =
    field[Pagination]("pagination")
      .zip(field[Seq[ArtifactResponse]]("items"))
      .xmap[Page[ArtifactResponse]] { case (pagination, artifacts) => Page(pagination, artifacts) } {
        case Page(pagination, artifacts) => (pagination, artifacts)
      }

  val artifactEndpointParams: QueryString[ArtifactParams] = (qs[Option[String]](
    name = "language",
    docs = Some(
      "Filter the results matching the given language version only (e.g., '3', '2.13', '2.12', '2.11', 'java')"
    )
  ) & qs[Option[String]](
    name = "platform",
    docs = Some("Filter the results matching the given platforms only (e.g., 'jvm', 'sjs1', 'native0.4', 'sbt1.0')")
  )).xmap((ArtifactParams.apply _).tupled)(Function.unlift(ArtifactParams.unapply))

  // Artifact endpoint definition
  val artifact: Endpoint[ArtifactParams, Page[ArtifactResponse]] =
    endpoint(
      get(path / "api" / "artifacts" /? artifactEndpointParams),
      ok(jsonResponse[Page[ArtifactResponse]])
    )
}
