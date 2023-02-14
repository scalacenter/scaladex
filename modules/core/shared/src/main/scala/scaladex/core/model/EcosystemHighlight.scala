package scaladex.core.model

final case class EcosystemVersion(version: SemanticVersion, deprecated: Boolean, libraryCount: Int, search: Url)

final case class EcosystemHighlight(
    ecosystem: String,
    currentVersion: EcosystemVersion,
    otherVersions: Seq[EcosystemVersion]
)
