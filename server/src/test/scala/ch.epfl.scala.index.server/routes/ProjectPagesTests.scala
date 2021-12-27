package ch.epfl.scala.index.server.routes

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterAll
import scaladex.core.test.Values

class ProjectPagesTests() extends ControllerBaseSuite with BeforeAndAfterAll with ScalatestRouteTest {
  import Values.PlayJsonExtra._

  override def beforeAll(): Unit =
    Await.result(insertMockData(), Duration.Inf)

  val projectPages = new ProjectPages(
    false,
    db = db,
    localStorage = localStorage,
    session = githubUserSession,
    paths = dataPaths,
    env = env
  )
  val routes = projectPages.routes

  describe("ProjectPageRoutes") {
    it("should return NotFound") {
      Get(s"/non-existing-org/non-existing-project}") ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    it("should redirect with the correct release") {
      Get(s"/$reference") ~> routes ~> check {
        status shouldEqual StatusCodes.TemporaryRedirect
        headers.head
          .value() shouldBe "/xuwei-k/play-json-extra/play-json-extra/0.1.1-play2.3-M1/?target=_2.11"
      }
    }
    it("should return StatusCodes.OK for org/repo/artifact") {
      Get(s"/$reference/play-json-extra") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    it("should return StatusCodes.OK for org/repo/artifact/version") {
      Get(s"/$reference/play-json-extra/0.1.1-play2.3-M1") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

  }
}
