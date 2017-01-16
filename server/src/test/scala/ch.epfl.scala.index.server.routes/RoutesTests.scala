package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.{RouteTest, TestFrameworkInterface}
import ch.epfl.scala.index.server.UserState
import org.specs2.execute.{Failure, FailureException}


object RouteTests extends org.specs2.mutable.Specification with SpecificationRouteTest {
  "routing of" >> {
    "/" >> {
      val route = new Paths(provide[Option[UserState]](None)).frontPagePath(_ => complete("Home Page"))

      Get() ~> route ~> check {
        responseAs[String] ==== "Home Page"
      }
    }
  }
}

trait SpecificationRouteTest extends RouteTest with SpecificationTestFrameworkInterface /*with MarshallingTestUtils*/ {
}

trait SpecificationTestFrameworkInterface extends TestFrameworkInterface {
  def cleanUp()

  // TODO: Refine start of stack trace by removing initial lines that are produced by the test framework
  def failTest(msg: String): Nothing = throw new FailureException(Failure(msg))
}