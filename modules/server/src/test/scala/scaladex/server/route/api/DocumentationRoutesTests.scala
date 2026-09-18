package scaladex.server.route.api

import scaladex.server.route.ControllerBaseSuite

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.Json
import org.apache.pekko.http.scaladsl.model.StatusCodes

class DocumentationRoutesTests extends ControllerBaseSuite with FailFastCirceSupport:
  describe("route") {
    it("should serve OpenAPI documentation") {
      Get("/api/open-api.json") ~> DocumentationRoute.route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Json] shouldNot be(null)
      }
    }

    it("should serve v1 OpenAPI documentation") {
      Get("/api/v1/open-api.json") ~> DocumentationRoute.route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Json] shouldNot be(null)
      }
    }
  }
end DocumentationRoutesTests
