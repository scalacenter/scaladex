package scaladex.server.route.api

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits.toTraverseOps
import endpoints4s.ujson.codecs.schemas.JsonSchema
import org.scalatest.BeforeAndAfterEach
import scaladex.core.api.artifact.ArtifactResponse
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.search.{Page, Pagination}
import scaladex.core.test.Values.Cats
import scaladex.core.test.Values.now
import scaladex.server.route.ControllerBaseSuite
import scaladex.server.route.api.ApiDocumentation.ujsonSchemas.field
import scaladex.server.route.api.ArtifactApi._

class ArtifactApiTests extends ControllerBaseSuite with BeforeAndAfterEach with PlayJsonSupport {

  val artifactRoute: Route = ArtifactApi(database).routes

  override protected def beforeAll(): Unit = Await.result(insertAllCatsArtifacts(), Duration.Inf)

  private def insertAllCatsArtifacts(): Future[Unit] =
    Cats.allArtifacts.traverse(database.insertArtifact(_, Seq.empty, now)).map(_ => ())

  describe("route") {
    it("should return all inserted artifacts, given no language or platform") {
      Get("/api/artifacts") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactResponse]]
        response match {
          case Page(Pagination(_, _, numStoredArtifacts), artifacts) =>
            numStoredArtifacts shouldBe Cats.allArtifacts.size
            artifacts.size shouldBe Cats.allArtifacts.size
            Cats.allArtifacts.forall { storedArtifact =>
              artifacts.exists { case ArtifactResponse(_, artifactId) => storedArtifact.artifactId == artifactId }
            } shouldBe true
        }
      }
    }

    it("should be able to query artifacts by their language") {
      val expectedResponse = Language
        .fromLabel("2.13")
        .map { targetLanguage =>
          Cats.allArtifacts.collect {
            case artifact if artifact.language == targetLanguage => (artifact.groupId.value, artifact.artifactId)
          }
        }
        .fold(Seq[(String, String)]())(identity)
      Get("/api/artifacts?language=2.13") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactResponse]]
        response match {
          case Page(_, artifacts) =>
            artifacts.size shouldBe 2
            artifacts.map {
              case ArtifactResponse(groupId, artifactId) => (groupId, artifactId)
            } should contain theSameElementsAs expectedResponse
        }
      }
    }

    it("should be able to query artifacts by their platform") {
      val expectedResponse = Platform
        .fromLabel("jvm")
        .map { targetPlatform =>
          Cats.allArtifacts.collect {
            case artifact if artifact.platform == targetPlatform => (artifact.groupId.value, artifact.artifactId)
          }
        }
        .fold(Seq[(String, String)]())(identity)
      Get("/api/artifacts?platform=jvm") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactResponse]]
        response match {
          case Page(_, artifacts) =>
            artifacts.size shouldBe 4
            artifacts.map {
              case ArtifactResponse(groupId, artifactId) => (groupId, artifactId)
            } should contain theSameElementsAs expectedResponse
        }
      }
    }

    it("should be able to query artifacts by their language and platform") {
      val expectedResponse =
        Seq(("org.typelevel", "cats-core_sjs0.6_2.13"), ("org.typelevel", "cats-core_native0.4_2.13"))
      Get("/api/artifacts?language=2.13&platform=sjs") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactResponse]]
        response match {
          case Page(_, artifacts) =>
            artifacts.size shouldBe 2
            artifacts.map {
              case ArtifactResponse(groupId, artifactId) => (groupId, artifactId)
            } should contain theSameElementsAs expectedResponse
        }
      }
    }

    it("should not return artifacts if the database is empty") {
      database.reset()
      Get(s"/api/artifacts") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactResponse]]
        response match {
          case Page(Pagination(_, _, numStoredArtifacts), artifacts) =>
            numStoredArtifacts shouldBe 0
            artifacts.size shouldBe 0
        }
      }
    }
  }
}
