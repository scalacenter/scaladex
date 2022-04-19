package scaladex.core.api.artifact

final case class ArtifactMetadataResponse(
    version: String,
    projectReference: Option[String],
    releaseDate: String,
    language: String,
    platform: String
)
