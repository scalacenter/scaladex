package ch.epfl.scala.index.api

case class SearchRequest(
    query: String,
    you: Boolean,
    topics: Seq[String],
    targetTypes: Seq[String],
    scalaVersions: Seq[String],
    scalaJsVersions: Seq[String],
    scalaNativeVersions: Seq[String],
    sbtVersions: Seq[String],
    contributingSearch: Boolean
) {
  def toHttpParams: Seq[(String, String)] = {
    Seq(s"q" -> query) ++
      optionalHttpParam(you, "you" -> "✓") ++
      toHttpParams("topics", topics) ++
      toHttpParams("targetTypes", targetTypes) ++
      toHttpParams("scalaVersions", scalaVersions) ++
      toHttpParams("scalaJsVersions", scalaJsVersions) ++
      toHttpParams("scalaNativeVersions", scalaNativeVersions) ++
      toHttpParams("sbtVersions", sbtVersions) ++
      optionalHttpParam(contributingSearch, "contributingSearch" -> "true")
  }

  private def toHttpParams(field: String, values: Seq[String]) =
    values.map(value => field -> value)

  private def optionalHttpParam(condition: Boolean, param: (String, String)) =
    if (condition) Some(param)
    else None
}

object SearchRequest {}
