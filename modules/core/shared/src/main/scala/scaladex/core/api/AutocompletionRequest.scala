package scaladex.core.api

import scaladex.core.model.Project
import scaladex.core.model.UserState
import scaladex.core.model.search.SearchParams
import scaladex.core.model.search.Sorting

case class AutocompletionRequest(
    query: String,
    you: Boolean,
    topics: Seq[String],
    languages: Seq[String],
    platforms: Seq[String],
    contributingSearch: Boolean
) {
  def searchParams(user: Option[UserState]): SearchParams = {
    val userRepos = if (you) user.map(_.repos).getOrElse(Set.empty) else Set.empty[Project.Reference]
    SearchParams(
      queryString = query,
      sorting = Sorting.Relevance,
      userRepos = userRepos,
      topics = topics,
      languages = languages,
      platforms = platforms,
      contributingSearch = contributingSearch
    )
  }
}
