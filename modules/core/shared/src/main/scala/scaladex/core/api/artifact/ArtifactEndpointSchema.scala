package scaladex.core.api.artifact

import scaladex.core.api.PaginationSchema

trait ArtifactEndpointSchema extends PaginationSchema {

  implicit val artifactResponseSchema: JsonSchema[ArtifactResponse] =
    field[String]("groupId")
      .zip(field[String]("artifactId"))
      .xmap[ArtifactResponse] { case (groupId, artifactId) => ArtifactResponse(groupId, artifactId) } {
        case ArtifactResponse(groupId, artifactId) => (groupId, artifactId)
      }

  implicit val artifactMetadataResponseSchema: JsonSchema[ArtifactMetadataResponse] =
    field[String]("version")
      .zip(optField[String]("projectReference"))
      .zip(field[String]("releaseDate"))
      .zip(field[String]("language"))
      .zip(field[String]("platform"))
      .xmap[ArtifactMetadataResponse](ArtifactMetadataResponse.tupled)(
        Function.unlift(ArtifactMetadataResponse.unapply)
      )
}
