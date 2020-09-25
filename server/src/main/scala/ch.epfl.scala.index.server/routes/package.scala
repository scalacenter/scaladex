package ch.epfl.scala.index.server

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import ch.epfl.scala.index.model.misc.SearchParams

package object routes {
  def searchParams(user: Option[UserState]): Directive1[SearchParams] =
    parameters(
      ("q" ? "*",
       "page".as[Int] ? 1,
       "sort".?,
       "topics".as[String].*,
       "targetTypes".as[String].*,
       "scalaVersions".as[String].*,
       "scalaJsVersions".as[String].*,
       "scalaNativeVersions".as[String].*,
       "sbtVersions".as[String].*,
       "you".?,
       "contributingSearch".as[Boolean] ? false)
    ).tmap {
      case (q,
            page,
            sort,
            topics,
            targetTypes,
            scalaVersions,
            scalaJsVersions,
            scalaNativeVersions,
            sbtVersions,
            you,
            contributingSearch) =>
        val userRepos = you
          .flatMap(_ => user.map(_.repos))
          .getOrElse(Set())
        SearchParams(
          q,
          page,
          sort,
          userRepos,
          topics = topics.toList,
          targetTypes = targetTypes.toList,
          scalaVersions = scalaVersions.toList,
          scalaJsVersions = scalaJsVersions.toList,
          scalaNativeVersions = scalaNativeVersions.toList,
          sbtVersions = sbtVersions.toList,
          contributingSearch = contributingSearch
        )
    }
}
