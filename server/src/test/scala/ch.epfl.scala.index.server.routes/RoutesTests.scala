package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import ch.epfl.scala.index.server.UserState
import ch.epfl.scala.index.server.routes.spec2.SpecificationRouteTest


object RouteTests extends org.specs2.mutable.Specification with SpecificationRouteTest {
  "routing of" >> {

    val behavior = new AlwaysCompleteBehavior {
      override val frontPage = (_: Any) => complete("Home Page")

      override val projectPage: (String, String, Option[UserState]) => StandardRoute =
        (org, proj, usr) => complete(s"Project: $org/$proj")

      override val artifactPageWithVersion: (String, String, String, String, Option[UserState]) => StandardRoute =
        (org, proj, art, ver, usr) => complete(s"Artifact: $org/$proj/$art/$ver")

      override val versionBadge: (String, String, String, Option[String], Option[String], Option[String], Option[Int]) => Route =
        (org, proj, art, _, _, _, _) => complete(s"You earned a badge for $org/$proj/$art!")
    }

    val baseUrl = "http://example.com"

    val route = new Paths(provide[Option[UserState]](None)).buildRoutes(behavior)

    "/ gives the Home Page" >>
      Get() ~> route ~> check {
        responseAs[String] ==== "Home Page"
      }

    "paths ending in '/' redirect appropriately" >>
      Get("/akka/") ~> route ~> check {
        status ==== StatusCodes.MovedPermanently
        header[Location] ==== Some(Location(baseUrl + "/akka"))
      }

    "projects should resolve appropriately" >>
      Get("/organization/project") ~> route ~> check {
        responseAs[String] ==== "Project: organization/project"
      }

    "artifacts with versions should resolve appropriately" >>
      Get("/organization/project/artifact/version") ~> route ~> check {
        responseAs[String] ==== "Artifact: organization/project/artifact/version"
      }

    "version badge succeeds" >>
      Get("/organization/project/artifact/latest.svg") ~> route ~> check {
        responseAs[String] ==== "You earned a badge for organization/project/artifact!"
      }
  }
}
