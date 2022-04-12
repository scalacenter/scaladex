package scaladex.core.api.artifact

import scaladex.core.api.PaginationSchema
import scaladex.core.model.search.Page
import scaladex.core.model.search.Pagination

trait ArtifactEndpointSchema extends PaginationSchema {

  implicit val artifactResponseSchema: JsonSchema[ArtifactResponse] =
    field[String]("groupId")
      .zip(field[String]("artifactId"))
      .xmap[ArtifactResponse] { case (groupId, artifactId) => ArtifactResponse(groupId, artifactId) } {
        case ArtifactResponse(groupId, artifactId) => (groupId, artifactId)
      }

  implicit val artifactEndpointResponse: JsonSchema[Page[ArtifactResponse]] =
    field[Pagination]("pagination")
      .zip(field[Seq[ArtifactResponse]]("items"))
      .xmap[Page[ArtifactResponse]] { case (pagination, artifacts) => Page(pagination, artifacts) } {
        case Page(pagination, artifacts) => (pagination, artifacts)
      }
}
