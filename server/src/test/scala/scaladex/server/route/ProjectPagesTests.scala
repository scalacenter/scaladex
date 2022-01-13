package scaladex.server.route

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.model.StatusCodes
import org.scalatest.BeforeAndAfterEach
import scaladex.core.test.Values

class ProjectPagesTests extends ControllerBaseSuite with BeforeAndAfterEach {
  import Values._

  def insertPlayJson(): Future[Unit] =
    for {
      _ <- database.insertArtifact(PlayJsonExtra.artifact, Seq.empty, Values.now)
      _ <- database.updateProjectCreationDate(PlayJsonExtra.reference, PlayJsonExtra.creationDate)
      _ <- database.updateGithubInfoAndStatus(PlayJsonExtra.reference, PlayJsonExtra.githubInfo, ok)
    } yield ()

  override def beforeEach(): Unit =
    Await.result(insertPlayJson(), Duration.Inf)

  val projectPages = new ProjectPages(
    env = config.env,
    database = database,
    localStorage = localStorage,
    session = githubUserSession
  )

  describe("GET organization/repository") {
    it("should return NotFound") {
      Get(s"/non-existing-org/non-existing-project}") ~> projectPages.routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }
    it("should redirect to the selected artifact") {
      Get(s"/${PlayJsonExtra.reference}") ~> projectPages.routes ~> check {
        status shouldEqual StatusCodes.TemporaryRedirect
        headers.head
          .value() shouldBe "/xuwei-k/play-json-extra/play-json-extra/0.1.1-play2.3-M1/?target=_2.11"
      }
    }
    it("should return StatusCodes.OK for org/repo/artifact") {
      Get(s"/${PlayJsonExtra.reference}/play-json-extra") ~> projectPages.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    it("should return StatusCodes.OK for org/repo/artifact/version") {
      Get(s"/${PlayJsonExtra.reference}/play-json-extra/0.1.1-play2.3-M1") ~> projectPages.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  describe("POST edit/orga/repo") {
    it("should replace empty customScalaDoc with None") {
      val formData = FormData(
        "primaryTopic" -> "serialization",
        "beginnerIssuesLabel" -> "",
        "chatroom" -> "",
        "contributingGuide" -> "",
        "codeOfConduct" -> "",
        "defaultArtifact" -> "play-json-extra",
        "defaultStableVersion" -> "on",
        "customScalaDoc" -> "",
        "documentationLinks[0].label" -> "",
        "documentationLinks[0].url" -> "",
        "documentationLinks[1].label" -> "",
        "documentationLinks[1].url" -> ""
      )
      Post(s"/edit/${PlayJsonExtra.reference}", formData) ~> projectPages.routes ~> check {
        status shouldBe StatusCodes.SeeOther
        for (project <- database.getProject(PlayJsonExtra.reference))
          yield {
            val settings = project.get.settings
            settings shouldBe PlayJsonExtra.settings
          }
      }
    }
  }
}
