package scaladex.core.model.search

import scaladex.core.api.AutocompletionRequest
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.UserState

case class SearchParams(
    queryString: String = "",
    page: Int = 0,
    sorting: Option[String] = None,
    userRepos: Set[Project.Reference] = Set(),
    total: Int = 20,
    targetFiltering: Option[Platform] = None,
    cli: Boolean = false,
    topics: Seq[String] = Nil,
    targetTypes: Seq[String] = Nil,
    scalaVersions: Seq[String] = Nil,
    scalaJsVersions: Seq[String] = Nil,
    scalaNativeVersions: Seq[String] = Nil,
    sbtVersions: Seq[String] = Nil,
    contributingSearch: Boolean = false
)

object SearchParams {

  def ofAutocompleteRequest(request: AutocompletionRequest, maybeUser: Option[UserState]): SearchParams = {
    val maybeUserRepos = if (request.you) maybeUser.map(_.repos) else None
    SearchParams(
      queryString = request.query,
      page = 1,
      sorting = None,
      userRepos = maybeUserRepos.getOrElse(Set()),
      topics = request.topics,
      targetTypes = request.targetTypes,
      scalaVersions = request.scalaVersions,
      scalaJsVersions = request.scalaJsVersions,
      scalaNativeVersions = request.scalaNativeVersions,
      sbtVersions = request.sbtVersions,
      contributingSearch = request.contributingSearch
    )
  }

}
