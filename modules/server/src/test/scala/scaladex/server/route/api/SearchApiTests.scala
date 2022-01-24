package scaladex.server.route.api

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import scaladex.core.test.Values
import scaladex.core.util.ScalaExtensions._
import scaladex.server.route.ControllerBaseSuite

class SearchApiTests extends ControllerBaseSuite with PlayJsonSupport {
  import Values._

  describe("parseScalaTarget") {
    it("should not recognize 3.x.y") {
      val res =
        SearchApi.parseScalaTarget(Some("JVM"), Some("3.0.1"), None, None, None)
      assert(res.isEmpty)
    }

    it("should not recognize scala3") {
      val res = SearchApi.parseScalaTarget(
        Some("JVM"),
        Some("scala3"),
        None,
        None,
        None
      )
      assert(res.isEmpty)
    }

    it("should recognize JVM/3") {
      val res =
        SearchApi.parseScalaTarget(Some("JVM"), Some("3"), None, None, None)
      assert(res.flatMap(_.scalaVersion.map(_.render)) == Some("scala 3"))
    }
  }

  def insertAllCatsArtifacts(): Future[Unit] =
    Cats.allArtifacts.map(database.insertArtifact(_, Seq.empty, now)).sequence.map(_ => ())

  describe("route") {
    it("should find project") {
      val searchApi = new SearchApi(searchEngine, database, githubUserSession)
      Await.result(insertAllCatsArtifacts(), Duration.Inf)

      Get("/api/project?organization=typelevel&repository=cats") ~> searchApi.routes ~> check {
        val result = responseAs[SearchApi.ArtifactOptions]
        (result.artifacts should contain).theSameElementsInOrderAs(Seq("cats-core", "cats-kernel", "cats-laws"))
        result.versions should contain theSameElementsAs Seq(`2.7.0`, `2.6.1`).map(_.toString)
        result.groupId shouldBe Cats.groupId.value
        result.artifactId shouldBe Cats.`core_3:2.7.0`.artifactId
        result.version shouldBe `2.7.0`.toString
      }
    }
  }
}
