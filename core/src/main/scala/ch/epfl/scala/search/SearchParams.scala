package ch.epfl.scala.search

import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewProject

case class SearchParams(
    queryString: String = "",
    page: Int = 0,
    sorting: Option[String] = None,
    userRepos: Set[NewProject.Reference] = Set(),
    total: Int = 20,
    targetFiltering: Option[Platform] = None,
    cli: Boolean = false,
    topics: List[String] = Nil,
    targetTypes: List[String] = Nil,
    scalaVersions: List[String] = Nil,
    scalaJsVersions: List[String] = Nil,
    scalaNativeVersions: List[String] = Nil,
    sbtVersions: List[String] = Nil,
    contributingSearch: Boolean = false
)
