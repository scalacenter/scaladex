package scaladex.server.route.api

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import scaladex.core.model.BinaryVersion
import scaladex.core.model.Jvm
import scaladex.core.model.Scala
import scaladex.core.test.Values
import scaladex.server.route.ControllerBaseSuite

class OldSearchApiTests extends ControllerBaseSuite with PlayJsonSupport {
  import Values._

  describe("parseBinaryVersion") {
    it("should not recognize 3.x.y") {
      val res =
        OldSearchApi.parseBinaryVersion(Some("JVM"), Some("3.0.1"), None, None, None)
      assert(res.isEmpty)
    }

    it("should not recognize scala3") {
      val res = OldSearchApi.parseBinaryVersion(Some("JVM"), Some("scala3"), None, None, None)
      assert(res.isEmpty)
    }

    it("should recognize JVM/3") {
      val res = OldSearchApi.parseBinaryVersion(Some("JVM"), Some("3"), None, None, None)
      assert(res == Some(BinaryVersion(Jvm, Scala.`3`)))
    }
  }

  def insertAllCatsArtifacts(): Future[Unit] =
    for {
      _ <- database.insertProjectRef(Cats.reference, unknown)
      _ <- Future.traverse(Cats.allArtifacts)(database.insertArtifact(_))
    } yield ()

  describe("route") {
    it("should find project") {
      val searchApi = new OldSearchApi(searchEngine, database)
      Await.result(insertAllCatsArtifacts(), Duration.Inf)

      Get("/api/project?organization=typelevel&repository=cats") ~> searchApi.routes ~> check {
        val result = responseAs[OldSearchApi.ArtifactOptions]
        (result.artifacts should contain).theSameElementsInOrderAs(Seq("cats-core", "cats-kernel", "cats-laws"))
        (result.versions should contain).theSameElementsInOrderAs(Seq(`2.7.0`, `2.6.1`).map(_.toString))
        result.groupId shouldBe Cats.groupId.value
        result.artifactId shouldBe Cats.`core_3:2.7.0`.artifactId
        result.version shouldBe `2.7.0`.toString
      }
    }
  }
}
