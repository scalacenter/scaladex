package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.server.Directives._
import ch.epfl.scala.index.server.UserState
import ch.epfl.scala.index.server.routes.spec2.SpecificationRouteTest


object RouteTests extends org.specs2.mutable.Specification with SpecificationRouteTest {
  "routing of" >> {
    "/" >> {
      val behavior = new AlwaysCompleteBehavior {
        override val frontPage = (_: Any) => complete("Home Page")
      }

      val route = new Paths(provide[Option[UserState]](None)).buildRoutes(behavior)

      Get() ~> route ~> check {
        responseAs[String] ==== "Home Page"
      }
    }
  }
}
