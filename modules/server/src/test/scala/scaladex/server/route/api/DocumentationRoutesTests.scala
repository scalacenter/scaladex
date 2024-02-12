package scaladex.server.route.api

import org.apache.pekko.http.scaladsl.model.StatusCodes
import play.api.libs.json.JsValue
import scaladex.server.route.ControllerBaseSuite

class DocumentationRoutesTests extends ControllerBaseSuite with PlayJsonSupport {

  describe("route") {
    it("should serve OpenAPI documentation") {
      Get("/api/open-api.json") ~> DocumentationRoutes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[JsValue] shouldNot be(null)
      }
    }
  }
}
