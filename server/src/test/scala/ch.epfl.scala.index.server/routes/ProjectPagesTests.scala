package ch.epfl.scala.index.server.routes

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.scala.index.server.Values
import org.scalatest.BeforeAndAfterAll

class ProjectPagesTests() extends ControllerBaseSuite with BeforeAndAfterAll with ScalatestRouteTest {

  override def beforeAll(): Unit =
    Await.result(insertMockData(), Duration.Inf)

  val projectPages = new ProjectPages(
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
      val project = Values.project
      Get(
        s"/${project.organization}/${project.repository}"
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.TemporaryRedirect
        headers.head
          .value() shouldBe "/xuwei-k/play-json-extra/play-json-extra/0.1.1-play2.3-M1/?target=_2.11"
      }
    }
    it("should return StatusCodes.OK for org/repo/artifact") {
      val project = Values.project
      Get(
        s"/${project.organization}/${project.repository}/play-json-extra"
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    it("should return StatusCodes.OK for org/repo/artifact/version") {
      val project = Values.project
      Get(
        s"/${project.organization}/${project.repository}/play-json-extra/0.1.1-play2.3-M1"
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

  }
}
