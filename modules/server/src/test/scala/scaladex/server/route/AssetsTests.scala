package scaladex.server.route

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.server.route.Assets

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
