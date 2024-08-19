package scaladex.server.route.api

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.scalatest.BeforeAndAfterEach
import scaladex.core.api.ArtifactResponse
import scaladex.core.model.Artifact
import scaladex.core.model.Project
import scaladex.core.test.Values._
import scaladex.server.route.ControllerBaseSuite

class ApiEndpointsImplTests extends ControllerBaseSuite with BeforeAndAfterEach {
  val endpoints = new ApiEndpointsImpl(database, searchEngine)
  import endpoints._

  override protected def beforeAll(): Unit = {
    val insertions = for {
      _ <- database.insertProjectRef(Cats.reference, unknown)
      _ <- Future.traverse(Cats.allArtifacts)(database.insertArtifact(_))
    } yield ()
    Await.result(insertions, Duration.Inf)
  }

  implicit def jsonCodecToUnmarshaller[T: JsonCodec]: FromEntityUnmarshaller[T] =
    Unmarshaller.stringUnmarshaller
      .forContentTypes(MediaTypes.`application/json`)
      .map(data => stringCodec[T].decode(data).toEither.toOption.get)

  it("list all project references") {
    Get("/api/projects") ~> routes(None) ~> check {
      status shouldBe StatusCodes.OK
      val artifacts = responseAs[Seq[Project.Reference]]
      val expected = Seq(Cats.reference)
      artifacts shouldBe expected
    }
  }

  it("list all artifact references of project") {
    Get(s"/api/projects/${Cats.reference}/artifacts") ~> routes(None) ~> check {
      status shouldBe StatusCodes.OK
      val artifacts = responseAs[Seq[Artifact.MavenReference]]
      val expected = Cats.allArtifacts.map(_.mavenReference)
      artifacts.size shouldBe expected.size
      artifacts.forall(expected.contains) shouldBe true
    }
  }

  it("empty list of artifacts for unknown project") {
    Get("/api/projects/unknown/unknown/artifacts") ~> routes(None) ~> check {
      status shouldBe StatusCodes.OK // TODO this should be not found
      val artifacts = responseAs[Seq[Artifact.MavenReference]]
      artifacts.size shouldBe 0
    }
  }

  it("unknown artifact") {
    Get("/api/artifacts/unknown/unknown_3/1.0.0") ~> routes(None) ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it("find artifact") {
    Get("/api/artifacts/org.typelevel/cats-core_3/2.6.1") ~> routes(None) ~> check {
      status shouldBe StatusCodes.OK
      val artifact = responseAs[ArtifactResponse]
      val expected = ArtifactResponse(Cats.`core_3:2.6.1`)
      artifact shouldBe expected
    }
  }
}
