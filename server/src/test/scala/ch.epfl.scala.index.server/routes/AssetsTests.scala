package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.scala.index.server.routes.Assets
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class AssetsTests extends AnyFunSpec with ScalatestRouteTest with Matchers {
  it("should return web-client script") {
    Get("/assets/webclient-fastopt.js") ~> Assets.routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] should not be empty
    }
  }

  it("should return web-client script map") {
    Get("/assets/webclient-fastopt.js.map") ~> Assets.routes ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] should not be empty
    }
  }
}
