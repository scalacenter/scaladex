package ch.epfl.scala.index.server.routes

import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterAll

class ProjectPagesTests()
    extends CtrlTests
    with BeforeAndAfterAll
    with ScalatestRouteTest {
  override def beforeAll(): Unit = insertMockData()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  val projectPages = new ProjectPages(
    db = db,
    session = githubUserSession,
    paths = dataPaths
  )
  val routes = projectPages.routes

  describe("ProjectPageRoutes") {
    it("should return NotFound") {
      val project = Values.project
      Get(s"/non-existing-org/non-existing-project}") ~> routes ~> check {
        status shouldEqual StatusCodes.NOT_FOUND
      }
    }
    it("should redirect with the correct release") {
      val project = Values.project
      Get(
        s"/${project.organization.value}/${project.repository.value}"
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.TEMPORARY_REDIRECT
        headers.head
          .value() shouldBe "/xuwei-k/play-json-extra/play-json-extra/0.1.1-play2.3-M1/?target=_2.11"
      }
    }

  }
}
