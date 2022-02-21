package scaladex.server.service

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact.MavenReference

class SonatypeSynchronizerTests extends AnyFunSpec with Matchers {
  describe("SonatypeSynchronizer") {
    it("should find missing version from sonatype") {
      val groupId = "org"
      val artifactId = "test"
      val mavenReferenceDatabase = Set(MavenReference(groupId, artifactId, "0.1"))
      val mavenReferenceSonatype =
        Seq(MavenReference(groupId, artifactId, "0.1"), MavenReference(groupId, artifactId, "0.2"))

      SonatypeSynchronizer.findMissingVersions(mavenReferenceDatabase, mavenReferenceSonatype) shouldBe Seq(
        MavenReference(groupId, artifactId, "0.2")
      )
    }
  }

}
