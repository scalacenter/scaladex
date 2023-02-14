package scaladex.core.model

final case class EcosystemVersion(version: SemanticVersion, deprecated: Boolean, libraryCount: Int, search: Url)

final case class EcosystemHighlight(
    ecosystem: String,
    description: Option[String],
    currentVersion: EcosystemVersion,
    otherVersions: Seq[EcosystemVersion],
    logo: Option[Url] = None
)
