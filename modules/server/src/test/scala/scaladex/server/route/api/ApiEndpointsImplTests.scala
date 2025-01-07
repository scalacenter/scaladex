package scaladex.server.route.api

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterEach
import scaladex.core.api.ArtifactResponse
import scaladex.core.model._
import scaladex.core.test.Values._
import scaladex.core.util.ScalaExtensions._
import scaladex.server.route.ControllerBaseSuite

class ApiEndpointsImplTests extends ControllerBaseSuite with BeforeAndAfterEach {
  val endpoints: ApiEndpointsImpl = new ApiEndpointsImpl(projectService, artifactService, searchEngine)
  import endpoints._

  override protected def beforeAll(): Unit = {
    val insertions = for {
      _ <- Cats.allArtifacts.mapSync(artifactService.insertArtifact(_, Seq.empty))
      _ <- searchSync.syncAll()
    } yield ()
    Await.result(insertions, Duration.Inf)
  }

  implicit def jsonCodecToUnmarshaller[T: JsonCodec]: FromEntityUnmarshaller[T] =
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
      import Cats._
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
      import Cats._
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
      import Cats._
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
  }

  private def testGet(route: String)(body: => Assertion): Unit =
    it(route)(Get(route) ~> routes(None) ~> check(body))
}
