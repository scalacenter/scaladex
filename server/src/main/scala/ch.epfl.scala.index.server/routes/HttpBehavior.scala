package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.{Directive1, Route, StandardRoute}
import ch.epfl.scala.index.data.github.GithubCredentials
import ch.epfl.scala.index.model.PageIndex
import ch.epfl.scala.index.server.UserState
import org.joda.time.DateTime

import scala.collection.immutable.Seq

trait HttpBehavior {
  val frontPage: (Option[UserState]) => StandardRoute
  val updateProject: (String, String, Option[UserState], Seq[(String, String)], Boolean, Iterable[String], Option[String], Boolean, Boolean, Iterable[String], Iterable[String], Option[String]) => Route
  val editProject: (String, String, Option[UserState]) => StandardRoute
  val projectPageArtifactQuery: (String, String, String, Option[String]) => StandardRoute
  val projectPage: (String, String, Option[UserState]) => StandardRoute
  val artifactPage: (String, String, String, Option[UserState]) => StandardRoute
  val artifactPageWithVersion: (String, String, String, String, Option[UserState]) => StandardRoute
  val searchResultsPage: (Option[UserState], String, Int, Option[String], Option[String]) => StandardRoute
  val organizationPage: (String) => StandardRoute
  val releaseStatus: (String) => StandardRoute
  val publishRelease: (String, DateTime, Boolean, Boolean, Boolean, Iterable[String], Boolean, String, (GithubCredentials, UserState)) => StandardRoute
  val projectSearchApi: (String, String, String, Option[String], Boolean) => StandardRoute
  val releaseInfoApi: (String, String, Option[String]) => StandardRoute
  val autocomplete: (String) => StandardRoute
  val versionBadge: (String, String, String, Option[String], Option[String], Option[String], Option[Int]) => Route
  val countBadge: (String, Option[String], Option[String], Option[String], Option[PageIndex], String) => Route
  val oAuth2routes: Route
  val credentialsTransformation: (Option[HttpCredentials]) => Directive1[(GithubCredentials, UserState)]
}
