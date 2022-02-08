package scaladex.core.model.search

import scaladex.core.model.BinaryVersion
import scaladex.core.model.Project

case class SearchParams(
    queryString: String = "",
    page: Int = 0,
    sorting: Option[String] = None,
    userRepos: Set[Project.Reference] = Set(),
    total: Int = 20,
    binaryVersion: Option[BinaryVersion] = None,
    cli: Boolean = false,
    topics: Seq[String] = Nil,
    languages: Seq[String] = Nil,
    platforms: Seq[String] = Nil,
    contributingSearch: Boolean = false
)
