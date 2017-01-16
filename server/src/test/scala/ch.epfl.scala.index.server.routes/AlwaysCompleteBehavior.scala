package ch.epfl.scala.index.server.routes
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.Directives.{complete, reject, provide}
import akka.http.scaladsl.server.{Directive1, Route, StandardRoute}
import ch.epfl.scala.index.data.github.GithubCredentials
import ch.epfl.scala.index.model.PageIndex
import ch.epfl.scala.index.server.UserState
import org.joda.time.DateTime

import scala.collection.immutable.Seq

class AlwaysCompleteBehavior extends HttpBehavior {
  val frontPage: (Option[UserState]) => StandardRoute = _ => complete("")
  val updateProject: (String, String, Option[UserState], Seq[(String, String)], Boolean, Iterable[String], Option[String], Boolean, Boolean, Iterable[String], Iterable[String], Option[String]) => Route = (_, _ , _, _, _, _, _, _, _, _, _, _) => complete("")
  val editProject: (String, String, Option[UserState]) => StandardRoute = (_, _ , _) => complete("")
  val projectPageArtifactQuery: (String, String, String, Option[String]) => StandardRoute = (_, _ , _, _) => complete("")
  val projectPage: (String, String, Option[UserState]) => StandardRoute = (_, _ , _) => complete("")
  val artifactPage: (String, String, String, Option[UserState]) => StandardRoute = (_, _ , _, _) => complete("")
  val artifactPageWithVersion: (String, String, String, String, Option[UserState]) => StandardRoute = (_, _ , _, _, _) => complete("")
  val searchResultsPage: (Option[UserState], String, Int, Option[String], Option[String]) => StandardRoute = (_, _ , _, _, _) => complete("")
  val organizationPage: (String) => StandardRoute = _ => complete("")
  val releaseStatus: (String) => StandardRoute = _ => complete("")
  val publishRelease: (String, DateTime, Boolean, Boolean, Boolean, Iterable[String], Boolean, String, (GithubCredentials, UserState)) => StandardRoute = (_, _ , _, _, _, _, _, _, _) => complete("")
  val projectSearchApi: (String, String, String, Option[String], Boolean) => StandardRoute = (_, _ , _, _, _) => complete("")
  val releaseInfoApi: (String, String, Option[String]) => StandardRoute = (_, _ , _) => complete("")
  val autocomplete: (String) => StandardRoute = _ => complete("")
  val versionBadge: (String, String, String, Option[String], Option[String], Option[String], Option[Int]) => Route = (_, _ , _, _, _, _, _) => complete("")
  val countBadge: (String, Option[String], Option[String], Option[String], Option[PageIndex], String) => Route = (_, _ , _, _, _, _) => complete("")
  val oAuth2routes: Route = reject()
  val credentialsTransformation: (Option[HttpCredentials]) => Directive1[(GithubCredentials, UserState)] = c => provide[(GithubCredentials, UserState)]((null, null))
}
