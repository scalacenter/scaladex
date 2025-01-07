package scaladex.core.api

import scaladex.core.model.*
import scaladex.core.model.search.SearchParams
import scaladex.core.model.search.Sorting

case class AutocompletionParams(
    query: String,
    topics: Seq[String],
    languages: Seq[Language],
    platforms: Seq[Platform],
    contributingSearch: Boolean,
    you: Boolean
):
  def withUser(user: Option[UserState]): SearchParams =
    val userRepos = if you then user.map(_.repos).getOrElse(Set.empty) else Set.empty[Project.Reference]
    SearchParams(
      queryString = query,
      sorting = Sorting.Stars,
      userRepos = userRepos,
      topics = topics,
      languages = languages,
      platforms = platforms,
      contributingSearch = contributingSearch
    )
  end withUser
end AutocompletionParams
