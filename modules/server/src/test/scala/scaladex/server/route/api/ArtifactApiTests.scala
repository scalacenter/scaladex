package scaladex.server.route.api

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.implicits.toTraverseOps
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Reads
import scaladex.core.api.artifact.ArtifactMetadataResponse
import scaladex.core.api.artifact.ArtifactResponse
import scaladex.core.model.Artifact
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.search.Page
import scaladex.core.model.search.Pagination
import scaladex.core.test.Values.Cats
import scaladex.core.test.Values.now
import scaladex.server.route.ControllerBaseSuite
import scaladex.server.util.PlayJsonCodecs

class ArtifactApiTests extends ControllerBaseSuite with BeforeAndAfterEach with PlayJsonSupport {

  val artifactRoute: Route = ArtifactApi(database).routes

  implicit val jsonPaginationReader: Reads[Pagination] = PlayJsonCodecs.paginationSchema.reads
  implicit def jsonArtifactResponsePageReader: Reads[Page[ArtifactResponse]] =
    PlayJsonCodecs.pageSchema[ArtifactResponse].reads
  implicit def jsonArtifactMetadataResponsePageReader: Reads[Page[ArtifactMetadataResponse]] =
    PlayJsonCodecs.pageSchema[ArtifactMetadataResponse].reads

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
            val distinctArtifacts = Cats.allArtifacts.distinctBy { artifact: Artifact =>
              (artifact.groupId.value, artifact.artifactId)
            }
            numStoredArtifacts shouldBe distinctArtifacts.size
            artifacts.size shouldBe distinctArtifacts.size
            distinctArtifacts.forall { storedArtifact =>
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
      val expectedResponseArtifacts = Platform
        .fromLabel("jvm")
        .map { targetPlatform =>
          val distinctArtifacts = Cats.allArtifacts.distinctBy { artifact: Artifact =>
            (artifact.groupId.value, artifact.artifactId)
          }
          distinctArtifacts.collect {
            case artifact if artifact.platform == targetPlatform => (artifact.groupId.value, artifact.artifactId)
          }
        }
        .fold(Seq[(String, String)]())(identity)
      Get("/api/artifacts?platform=jvm") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactResponse]]
        response match {
          case Page(_, artifacts) =>
            artifacts.size shouldBe expectedResponseArtifacts.size
            artifacts.map {
              case ArtifactResponse(groupId, artifactId) => (groupId, artifactId)
            } should contain theSameElementsAs expectedResponseArtifacts
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

    it("should not return any artifacts given a group id and artifact id for an artifact not stored") {
      Get("/api/artifacts/ca.ubc.cs/test-package_3") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactMetadataResponse]]
        response shouldBe Page.empty[ArtifactMetadataResponse]
      }
    }

    it("should not return any artifacts given a valid group id and an artifact id that does not parse") {
      val malformedArtifactId = "badArtifactId"
      Get(s"/api/artifacts/org.apache.spark/$malformedArtifactId") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactMetadataResponse]]
        response shouldBe Page.empty[ArtifactMetadataResponse]
      }
    }

    it("should not return any artifacts given an invalid group id and a valid artifact id") {
      Get("/api/artifacts/ca.ubc.cs/cats-core_3") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactMetadataResponse]]
        response shouldBe Page.empty[ArtifactMetadataResponse]
      }
    }

    it("should return artifacts with the given group id and artifact id") {
      Get("/api/artifacts/org.typelevel/cats-core_3") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactMetadataResponse]]
        val expectedArtifactMetadata = Seq(Cats.`core_3:2.6.1`, Cats.`core_3:2.7.0`).map(Artifact.toMetadataResponse)
        response match {
          case Page(_, artifacts) =>
            artifacts.size shouldBe 2
            artifacts should contain theSameElementsAs expectedArtifactMetadata
        }
      }
    }

    it("should not return artifacts if the database is empty") {
      database.reset()
      Get(s"/api/artifacts") ~> artifactRoute ~> check {
        status shouldBe StatusCodes.OK
        val response = responseAs[Page[ArtifactResponse]]
        response shouldBe Page.empty[ArtifactResponse]
      }
    }
  }
}
