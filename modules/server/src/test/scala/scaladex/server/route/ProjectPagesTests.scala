package scaladex.server.route

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.apache.pekko.http.scaladsl.model.FormData
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.headers.Location
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.scalatest.BeforeAndAfterEach
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.test.Values

class ProjectPagesTests extends ControllerBaseSuite with BeforeAndAfterEach {
  import Values._

  override def beforeEach(): Unit = {
    database.reset()
    Await.result(insertPlayJsonExtra(), Duration.Inf)
  }

  private def insertPlayJsonExtra(): Future[Unit] =
    for {
      _ <- database.insertArtifact(PlayJsonExtra.artifact, Seq.empty, Values.now)
      _ <- database.updateProjectCreationDate(PlayJsonExtra.reference, PlayJsonExtra.creationDate)
      _ <- database.updateGithubInfoAndStatus(PlayJsonExtra.reference, PlayJsonExtra.githubInfo, ok)
    } yield ()

  val projectPages = new ProjectPages(config.env, database, searchEngine)
  val artifactPages = new ArtifactPages(config.env, database)
  val route: Route = projectPages.route(None) ~ artifactPages.route(None)

  it("should return NotFound") {
    Get(s"/non-existing-org/non-existing-project}") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }
  it("should return StatusCodes.OK for /<org/repo>") {
    Get(s"/${PlayJsonExtra.reference}") ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
  }
  it("should return StatusCodes.OK for /<org/repo>/artifacts/<artifact-name>") {
    Get(s"/${PlayJsonExtra.reference}/artifacts/play-json-extra") ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
  }
  it("should return StatusCodes.OK for /<org/repo>/artifacts/<artifact-name>/<version>") {
    Get(s"/${PlayJsonExtra.reference}/artifacts/play-json-extra/0.1.1-play2.3-M1") ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  it("should redirect on moved project") {
    val destination = PlayJsonExtra.reference.copy(repository = Project.Repository("play-json-extra-2"))
    val moved = GithubStatus.Moved(now, destination)
    Await.result(database.moveProject(PlayJsonExtra.reference, PlayJsonExtra.githubInfo, moved), Duration.Inf)
    Get(s"/${PlayJsonExtra.reference}/artifacts/play-json-extra?binary-versions=_2.13") ~> route ~> check {
      status shouldEqual StatusCodes.MovedPermanently
      val location = headers.collectFirst { case Location(uri) => uri }
      location should contain(Uri(s"http://example.com/$destination/artifacts/play-json-extra?binary-versions=_2.13"))
    }
  }

  describe("POST /<orga/repo>/settings") {
    it("should replace empty customScalaDoc with None") {
      val formData = FormData(
        "category" -> "json",
        "chatroom" -> "",
        "defaultArtifact" -> "play-json-extra",
        "preferStableVersion" -> "on",
        "customScalaDoc" -> "",
        "documentationLinks[0].label" -> "",
        "documentationLinks[0].url" -> "",
        "documentationLinks[1].label" -> "",
        "documentationLinks[1].url" -> ""
      )
      Post(s"/${PlayJsonExtra.reference}/settings", formData) ~> route ~> check {
        status shouldBe StatusCodes.SeeOther
        for (project <- database.getProject(PlayJsonExtra.reference)) yield {
          val settings = project.get.settings
          settings shouldBe PlayJsonExtra.settings
        }
      }
    }
  }
}
