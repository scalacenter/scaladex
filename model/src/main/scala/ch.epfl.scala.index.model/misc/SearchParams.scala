package ch.epfl.scala.index.model
package misc

import release.ScalaTarget

object SearchParams {
  val resultsPerPage = 20
}

case class SearchParams(
    queryString: String = "",
    page: PageIndex = 0,
    sorting: Option[String] = None,
    userRepos: Set[GithubRepo] = Set(),
    total: Int = SearchParams.resultsPerPage,
    targetFiltering: Option[ScalaTarget] = None,
    cli: Boolean = false,
    keywords: List[String] = Nil,
    targetTypes: List[String] = Nil,
    scalaVersions: List[String] = Nil,
    scalaJsVersions: List[String] = Nil,
    scalaNativeVersions: List[String] = Nil
)
