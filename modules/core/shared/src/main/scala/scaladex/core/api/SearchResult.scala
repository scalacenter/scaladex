package scaladex.core.api

import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.Version

/** A single project matched by the search API. */
case class SearchResult(
    organization: Project.Organization,
    repository: Project.Repository,
    description: Option[String],
    stars: Option[Int],
    forks: Option[Int],
    topics: Seq[String],
    languages: Seq[Language],
    platforms: Seq[Platform],
    latestVersion: Option[Version],
    dependents: Long,
    category: Option[String]
)
