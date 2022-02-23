package scaladex.core.model.search

import scaladex.core.model.Project

case class SearchParams(
    queryString: String = "",
    sorting: Sorting = Sorting.Relevance,
    userRepos: Set[Project.Reference] = Set(),
    topics: Seq[String] = Nil,
    languages: Seq[String] = Nil,
    platforms: Seq[String] = Nil,
    contributingSearch: Boolean = false
)
