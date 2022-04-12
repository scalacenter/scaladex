package scaladex.server.util

import scaladex.core.api.PaginationSchema
import scaladex.core.api.artifact.ArtifactEndpointSchema

object PlayJsonCodecs extends PaginationSchema with ArtifactEndpointSchema with endpoints4s.playjson.JsonSchemas
