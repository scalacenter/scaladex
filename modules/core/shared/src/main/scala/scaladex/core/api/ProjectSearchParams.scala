package scaladex.core.api

import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.search.SearchParams
import scaladex.core.model.search.Sorting

case class ProjectSearchParams(
    query: String,
    topics: Seq[String],
    languages: Seq[Language],
    platforms: Seq[Platform],
    sort: Option[String]
):
  def toSearchParams: SearchParams = SearchParams(
    queryString = query,
    sorting = sort.flatMap(Sorting.byLabel.get).getOrElse(Sorting.Stars),
    topics = topics,
    languages = languages,
    platforms = platforms
  )
end ProjectSearchParams
