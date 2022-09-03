package scaladex.core.model

import java.time.Instant

import scaladex.core.model.Project._

case class Release(
    organization: Organization,
    repository: Repository,
    platform: Platform,
    language: Language,
    version: SemanticVersion,
    releaseDate: Instant
)
