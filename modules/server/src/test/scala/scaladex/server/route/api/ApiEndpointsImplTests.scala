package scaladex.server.route.api

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import scaladex.core.api.ArtifactResponse
import scaladex.core.api.SearchResult
import scaladex.core.api.UserResponse
import scaladex.core.model.*
import scaladex.core.model.search.Page
import scaladex.core.model.search.PageParams
import scaladex.core.service.GithubAuth
import scaladex.core.test.MockGithubAuth
import scaladex.core.test.Values.*
import scaladex.core.util.ScalaExtensions.*
import scaladex.core.util.Secret
import scaladex.server.route.ControllerBaseSuite

import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterEach

class ApiEndpointsImplTests extends ControllerBaseSuite with BeforeAndAfterEach:
  // Env.Prod so that edit permissions are actually enforced (in a local env every user is an admin)
  val endpoints: ApiEndpointsImpl =
    new ApiEndpointsImpl(Env.Prod, projectService, artifactService, settingsService, searchEngine, githubAuth)
  import endpoints.*

  val typelevel: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Typelevel.token)
  val sonatype: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Sonatype.token)
  val admin: BasicHttpCredentials = BasicHttpCredentials("token", MockGithubAuth.Admin.token)

  val failingGithubAuth: GithubAuth = new GithubAuth:
    def getToken(code: String): Future[Secret] = Future.failed(new Exception("unavailable"))
    def getUser(token: Secret): Future[UserInfo] = Future.failed(new Exception("unavailable"))
    def getUserState(token: Secret): Future[Option[UserState]] = Future.failed(new Exception("GitHub is unavailable"))

  override protected def beforeAll(): Unit =
    val insertions = for
      _ <- Cats.allArtifacts.mapSync(artifactService.insertArtifact(_, Seq.empty))
      _ <- searchSync.syncAll()
    yield ()
    Await.result(insertions, Duration.Inf)

  given [T: JsonCodec]: FromEntityUnmarshaller[T] =
    Unmarshaller.stringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .map(data => stringCodec[T].decode(data).toEither.toOption.get)

  describe("v0") {
    testGet("/api/projects") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Project.Reference]] shouldBe Seq(Cats.reference)
    }

    testGet(s"/api/projects/${Cats.reference}/artifacts") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Artifact.Reference]] should contain theSameElementsAs Cats.allArtifacts.map(_.reference)
    }

    testGet("/api/projects/unknown/unknown/artifacts") {
      status shouldBe StatusCodes.OK // TODO this should be not found
      val artifacts = responseAs[Seq[Artifact.Reference]]
      artifacts shouldBe empty
    }

    testGet("/api/artifacts/org.typelevel/cats-core_3/2.6.1") {
      status shouldBe StatusCodes.OK
      responseAs[ArtifactResponse] shouldBe Cats.`core_3:2.6.1`.toResponse
    }

    testGet("/api/artifacts/unknown/unknown_3/1.0.0") {
      status shouldBe StatusCodes.NotFound
    }
  }

  describe("v1") {
    testGet("/api/v1/projects") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Project.Reference]] shouldBe Seq(Cats.reference)
    }

    testGet("/api/v1/projects?platform=jvm&language=3") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Project.Reference]] shouldBe Seq(Cats.reference)
    }

    testGet("/api/v1/projects?platform=sbt1&platform=jvm") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Project.Reference]] shouldBe empty
    }

    testGet("/api/v1/projects?language=3&language=2.12") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Project.Reference]] shouldBe empty
    }

    // fail parsing platform
    testGet("/api/v1/projects?platform=foo") {
      status shouldBe StatusCodes.BadRequest
      // TODO should return error message
    }

    // fail parsing language
    testGet("/api/v1/projects?language=bar") {
      status shouldBe StatusCodes.BadRequest
      // TODO should return error message
    }

    testGet(s"/api/v1/projects/${Cats.reference}/versions") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Version]] should contain theSameElementsAs Seq(`2.6.1`, `2.5.0`)
    }

    testGet(s"/api/v1/projects/${Cats.reference}/versions?binary-version=_sjs1_3") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Version]] should contain theSameElementsAs Seq(`2.6.1`)
    }

    testGet(s"/api/v1/projects/${Cats.reference}/versions?artifact-name=cats-kernel") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Version]] should contain theSameElementsAs Seq(`2.6.1`)
    }

    testGet(
      s"/api/v1/projects/${Cats.reference}/versions?binary-version=_3&binary-version=_sjs1_3&artifact-name=cats-core"
    ) {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Version]] should contain theSameElementsAs Seq(`2.6.1`)
    }

    testGet(
      s"/api/v1/projects/${Cats.reference}/versions?binary-version=_3&artifact-name=cats-kernel&artifact-name=cats-core"
    ) {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Version]] should contain theSameElementsAs Seq(`2.6.1`)
    }

    testGet("/api/v1/projects/unknown/unknown/versions") {
      status shouldBe StatusCodes.OK // TODO this should be not found
      responseAs[Seq[Version]] shouldBe empty
    }

    testGet(s"/api/v1/projects/${Cats.reference}/versions/latest") {
      status shouldBe StatusCodes.OK
      import Cats.*
      val expected = Seq(
        `core_3:2.6.1`,
        `core_sjs1_3:2.6.1`,
        `core_sjs06_2.13:2.6.1`,
        `core_native04_2.13:2.6.1`,
        `kernel_3:2.6.1`,
        `laws_3:2.6.1`
      ).map(_.reference)
      responseAs[Seq[Artifact.Reference]] should contain theSameElementsAs expected
    }

    testGet(s"/api/v1/projects/${Cats.reference}/versions/2.6.1") {
      status shouldBe StatusCodes.OK
      val expected = Cats.allArtifacts.filter(_.version == `2.6.1`).map(_.reference)
      responseAs[Seq[Artifact.Reference]] should contain theSameElementsAs expected
    }

    testGet(s"/api/v1/projects/${Cats.reference}/artifacts") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Artifact.Reference]] should contain theSameElementsAs Cats.allArtifacts.map(_.reference)
    }

    testGet(s"/api/v1/projects/${Cats.reference}/artifacts?binary-version=_3") {
      status shouldBe StatusCodes.OK
      import Cats.*
      val expected = Seq(`core_3:2.6.1`, `kernel_3:2.6.1`, `laws_3:2.6.1`).map(_.reference)
      responseAs[Seq[Artifact.Reference]] should contain theSameElementsAs expected
    }

    testGet(s"/api/v1/projects/${Cats.reference}/artifacts?artifact-name=cats-core") {
      status shouldBe StatusCodes.OK
      val expected = Cats.coreArtifacts.map(_.reference)
      responseAs[Seq[Artifact.Reference]] should contain theSameElementsAs expected
    }

    testGet(s"/api/v1/projects/${Cats.reference}/artifacts?artifact-name=cats-core&binary-version=_3") {
      status shouldBe StatusCodes.OK
      import Cats.*
      val expected = Seq(`core_3:2.6.1`).map(_.reference)
      responseAs[Seq[Artifact.Reference]] should contain theSameElementsAs expected
    }

    testGet("/api/v1/projects/unknown/unknown/artifacts") {
      status shouldBe StatusCodes.OK // TODO this should be not found
      responseAs[Seq[Artifact.Reference]] shouldBe empty
    }

    testGet("/api/v1/projects/unknown/unknown/artifacts?binary-version=foo") {
      status shouldBe StatusCodes.BadRequest // failed to parse binaryVersion
      // TODO return error message
    }

    testGet("/api/v1/artifacts/org.typelevel/cats-core_3") {
      status shouldBe StatusCodes.OK
      responseAs[Seq[Version]] should contain theSameElementsAs Seq(`2.6.1`)
    }

    testGet("/api/v1/artifacts/org.typelevel/cats-core_3/latest") {
      status shouldBe StatusCodes.OK
      responseAs[ArtifactResponse] shouldBe Cats.`core_3:2.6.1`.toResponse
    }

    testGet("/api/v1/artifacts/org.typelevel/cats-core_2.13/2.5.0") {
      status shouldBe StatusCodes.OK
      responseAs[ArtifactResponse] shouldBe Cats.`core_2.13:2.5.0`.toResponse
    }

    testGet("/api/v1/artifacts/unknown/unknown_3/1.0.0") {
      status shouldBe StatusCodes.NotFound
    }

    testGet(s"/api/v1/projects/${Cats.reference}/dependencies?page=1&size=20") {
      status shouldBe StatusCodes.OK
      val page = responseAs[Page[ProjectDependency]]
      page.pagination.current shouldBe 1
      page.items shouldBe empty // the in-memory database has no project dependencies
    }

    testGet("/api/v1/projects/unknown/unknown/dependencies") {
      status shouldBe StatusCodes.NotFound
    }

    testGet(s"/api/v1/projects/${Cats.reference}/dependents") {
      status shouldBe StatusCodes.OK
      responseAs[Page[ProjectDependency]].pagination.current shouldBe 1
    }

    testGet("/api/v1/projects/unknown/unknown/dependents") {
      status shouldBe StatusCodes.NotFound
    }

    testGet("/api/v1/search?q=cats") {
      status shouldBe StatusCodes.OK
      val page = responseAs[Page[SearchResult]]
      page.items.map(_.repository) should contain(Cats.reference.repository)
    }

    testGet("/api/v1/search?q=*&sort=stars&page=1&size=50") {
      status shouldBe StatusCodes.OK
      responseAs[Page[SearchResult]].items.size should be <= 50
    }

    testGet("/api/v1/search?q=cats&sort=not-a-sort") {
      status shouldBe StatusCodes.OK // an unknown sort falls back to the default rather than failing
    }

    for size <- PageParams.AllowedSizes do
      testGet(s"/api/v1/projects/${Cats.reference}/dependents?size=$size") {
        status shouldBe StatusCodes.OK
      }

    for size <- Seq(-5, 0, 5, 30, 100000) do
      testGet(s"/api/v1/projects/${Cats.reference}/dependents?size=$size") {
        status shouldBe StatusCodes.BadRequest // only PageParams.AllowedSizes is accepted
      }

    testGet(s"/api/v1/projects/${Cats.reference}/dependents?page=0") {
      status shouldBe StatusCodes.OK
      responseAs[Page[ProjectDependency]].pagination.current shouldBe 1 // page is clamped to at least 1
    }
  }

  describe("v1 authenticated") {
    it("GET /api/v1/users/me requires credentials") {
      Get("/api/v1/users/me") ~> routes(None) ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    it("GET /api/v1/users/me rejects an unknown token") {
      Get("/api/v1/users/me").addCredentials(BasicHttpCredentials("token", "nope")) ~> routes(None) ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    it("GET /api/v1/users/me returns the authenticated user") {
      Get("/api/v1/users/me").addCredentials(typelevel) ~> routes(None) ~> check {
        status shouldBe StatusCodes.OK
        val user = responseAs[UserResponse]
        user.login shouldBe MockGithubAuth.Typelevel.info.login
        user.repositories should contain(Cats.reference)
      }
    }

    it("GET settings is forbidden for a user without edit permission") {
      Get(s"/api/v1/projects/${Cats.reference}/settings").addCredentials(sonatype) ~> routes(None) ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    it("GET settings returns the current settings") {
      Get(s"/api/v1/projects/${Cats.reference}/settings").addCredentials(typelevel) ~> routes(None) ~> check {
        status shouldBe StatusCodes.OK
        responseAs[Project.Settings] shouldBe Project.Settings.empty
      }
    }

    it("GET settings is 404 for an unknown project (admin)") {
      Get("/api/v1/projects/unknown/unknown/settings").addCredentials(admin) ~> routes(None) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    it("GET settings is 404 for an unknown project even without edit permission") {
      Get("/api/v1/projects/unknown/unknown/settings").addCredentials(typelevel) ~> routes(None) ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    it("returns 500 (not 403) when GitHub cannot be reached") {
      val failing = new ApiEndpointsImpl(
        Env.Prod,
        projectService,
        artifactService,
        settingsService,
        searchEngine,
        failingGithubAuth
      )
      Get("/api/v1/users/me").addCredentials(typelevel) ~> failing.routes(None) ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }

    it("PATCH settings updates only the provided fields") {
      val body = HttpEntity(ContentTypes.`application/json`, """{"contributorsWanted":true,"chatroom":"#cats"}""")
      Patch(s"/api/v1/projects/${Cats.reference}/settings", body).addCredentials(typelevel) ~> routes(None) ~> check {
        status shouldBe StatusCodes.OK
        for settings <- projectService.getSettings(Cats.reference)
        yield
          settings.map(_.contributorsWanted) shouldBe Some(true)
          settings.flatMap(_.chatroom) shouldBe Some("#cats")
      }
    }

    it("PATCH settings clears a field with an empty string and leaves absent fields untouched") {
      val ref = Cats.reference
      val setup = HttpEntity(ContentTypes.`application/json`, """{"contributorsWanted":true,"chatroom":"#room"}""")
      Patch(s"/api/v1/projects/$ref/settings", setup).addCredentials(typelevel) ~> routes(None) ~> check {
        status shouldBe StatusCodes.OK
      }
      val clear = HttpEntity(ContentTypes.`application/json`, """{"chatroom":""}""")
      Patch(s"/api/v1/projects/$ref/settings", clear).addCredentials(typelevel) ~> routes(None) ~> check {
        status shouldBe StatusCodes.OK
        for settings <- projectService.getSettings(ref)
        yield
          settings.flatMap(_.chatroom) shouldBe None
          settings.map(_.contributorsWanted) shouldBe Some(true) // untouched: not part of the second patch
      }
    }

    it("PATCH settings rejects an unknown category") {
      val body = HttpEntity(ContentTypes.`application/json`, """{"category":"not-a-category"}""")
      Patch(s"/api/v1/projects/${Cats.reference}/settings", body).addCredentials(typelevel) ~> routes(None) ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    it("PATCH settings is forbidden without edit permission") {
      val body = HttpEntity(ContentTypes.`application/json`, """{"contributorsWanted":true}""")
      Patch(s"/api/v1/projects/${Cats.reference}/settings", body).addCredentials(sonatype) ~> routes(None) ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }
  }

  private def testGet(route: String)(body: => Assertion): Unit =
    it(route)(Get(route) ~> routes(None) ~> check(body))
end ApiEndpointsImplTests
