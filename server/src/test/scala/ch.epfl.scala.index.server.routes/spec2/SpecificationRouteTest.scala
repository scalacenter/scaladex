package ch.epfl.scala.index.server.routes.spec2

import akka.http.scaladsl.testkit.RouteTest

trait SpecificationRouteTest extends RouteTest with SpecificationTestFrameworkInterface /*with MarshallingTestUtils*/ {
}
