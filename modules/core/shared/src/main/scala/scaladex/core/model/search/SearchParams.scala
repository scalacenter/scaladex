package scaladex.core.model.search

import scaladex.core.api.AutocompletionParams
import scaladex.core.model._

case class SearchParams(
    queryString: String = "",
    sorting: Sorting = Sorting.Stars,
    userRepos: Set[Project.Reference] = Set(),
    topics: Seq[String] = Nil,
    languages: Seq[Language] = Nil,
    platforms: Seq[Platform] = Nil,
    contributingSearch: Boolean = false
) {
  def toAutocomplete: AutocompletionParams = AutocompletionParams(
    queryString,
    topics,
    languages,
    platforms,
    contributingSearch,
    userRepos.nonEmpty
  )
}
