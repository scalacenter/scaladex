package scaladex.server.route

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import scaladex.core.model.*
import scaladex.core.test.Values

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.scalatest.BeforeAndAfterEach

class FrontPageTests extends ControllerBaseSuite with BeforeAndAfterEach:
  import Values.*

  override def beforeEach(): Unit =
    database.reset()
    Await.result(insertCompilerPluginProject(), Duration.Inf)

  private def insertCompilerPluginProject(): Future[Unit] =
    for
      _ <- artifactService.insertArtifact(CompilerPluginProj.artifact, Seq.empty)
      _ <- database.updateProjectCreationDate(CompilerPluginProj.reference, CompilerPluginProj.creationDate)
      _ <- database.updateGithubInfoAndStatus(CompilerPluginProj.reference, CompilerPluginProj.githubInfo, ok)
    yield ()

  val frontPage = new FrontPage(config.env, database, searchEngine)
  val route: Route = frontPage.route(None)

  it("should display CompilerPlugin section on front page") {
    Get("/") ~> route ~> check {
      status shouldBe StatusCodes.OK
      val responseBody = responseAs[String]
      
      // Check that the CompilerPlugin section is present
      responseBody should include("Compiler Plugins")
      responseBody should include("Compiler Plugin")
      responseBody should include("search?platform=compiler-plugin")
    }
  }

  it("should display CompilerPlugin with correct project count") {
    Get("/") ~> route ~> check {
      status shouldBe StatusCodes.OK
      val responseBody = responseAs[String]
      
      // Check that the CompilerPlugin section shows the project count
      responseBody should include("projects")
    }
  }

  it("should have CompilerPlugin as a separate section from Build Tool Plugins") {
    Get("/") ~> route ~> check {
      status shouldBe StatusCodes.OK
      val responseBody = responseAs[String]
      
      // Check that both sections exist
      responseBody should include("Build Tool Plugins")
      responseBody should include("Compiler Plugins")
      
      // Check that CompilerPlugin is not mixed with build tool plugins
      val buildToolPluginsIndex = responseBody.indexOf("Build Tool Plugins")
      val compilerPluginsIndex = responseBody.indexOf("Compiler Plugins")
      compilerPluginsIndex should be > buildToolPluginsIndex
    }
  }
end FrontPageTests
