package scaladex.server.service

import scaladex.core.model.Version
import scaladex.core.test.InMemoryDatabase

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ArtifactServiceTests extends AsyncFunSpec with Matchers:
  val database: InMemoryDatabase = new InMemoryDatabase()
  val artifactService = new ArtifactService(database)

  describe("computeLatestVersion") {
    it("should return None for empty sequence") {
      val result = artifactService.computeLatestVersion(Seq.empty, preferStableVersion = false)
      result shouldBe None
    }

    it("should return None for empty sequence with preferStableVersion = true") {
      val result = artifactService.computeLatestVersion(Seq.empty, preferStableVersion = true)
      result shouldBe None
    }

    it("should return the maximum version when preferStableVersion is false") {
      val versions = Seq(Version("1.0.0"), Version("2.0.0"), Version("1.5.0"))
      val result = artifactService.computeLatestVersion(versions, preferStableVersion = false)
      result shouldBe Some(Version("2.0.0"))
    }

    it("should return the maximum stable version when preferStableVersion is true and stable versions exist") {
      val versions = Seq(Version("1.0.0"), Version("2.0.0"), Version("3.0.0-RC1"))
      val result = artifactService.computeLatestVersion(versions, preferStableVersion = true)
      result shouldBe Some(Version("2.0.0"))
    }

    it("should return the maximum version when preferStableVersion is true but no stable versions exist") {
      val versions = Seq(Version("1.0.0-RC1"), Version("2.0.0-SNAPSHOT"), Version("1.5.0-M1"))
      val result = artifactService.computeLatestVersion(versions, preferStableVersion = true)
      result shouldBe Some(Version("2.0.0-SNAPSHOT"))
    }

    it("should handle a single version") {
      val versions = Seq(Version("1.0.0"))
      val result = artifactService.computeLatestVersion(versions, preferStableVersion = false)
      result shouldBe Some(Version("1.0.0"))
    }

    it("should handle all unstable versions with preferStableVersion = false") {
      val versions = Seq(Version("1.0.0-RC1"), Version("2.0.0-SNAPSHOT"))
      val result = artifactService.computeLatestVersion(versions, preferStableVersion = false)
      result shouldBe Some(Version("2.0.0-SNAPSHOT"))
    }

    it("should prefer stable version over higher unstable version") {
      val versions = Seq(Version("1.0.0"), Version("2.0.0-RC1"))
      val result = artifactService.computeLatestVersion(versions, preferStableVersion = true)
      result shouldBe Some(Version("1.0.0"))
    }
  }
end ArtifactServiceTests
