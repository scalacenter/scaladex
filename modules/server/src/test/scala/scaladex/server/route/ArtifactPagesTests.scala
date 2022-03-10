package scaladex.server.route

import org.scalatest.funspec.AnyFunSpec
import scaladex.core.model.Artifact.Name
import scaladex.core.model._

class ArtifactPagesTests extends AnyFunSpec {
  describe("ArtifactPages") {
    it("should find the default artifact 1") {
      val artifact1 = (Name("sbt-scalafix"), SbtPlugin.`1.0`, Scala.`2.12`)
      val artifact2 = (Name("scalafix-core"), Jvm, Scala.`2.12`)
      val res = ArtifactPages.getDefaultArtifact(Seq(artifact1, artifact2))
      assert(res == artifact2._1)
    }
    it("should find the default artifact 2") {
      val artifact1 = (Name("scalafix-core"), Jvm, Scala.`2.13`)
      val artifact2 = (Name("scalafix-core"), Jvm, Scala.`2.12`)
      val res = ArtifactPages.getDefaultArtifact(Seq(artifact1, artifact2))
      assert(res == artifact1._1)
    }
    it("should find the default artifact 3") {
      val artifact1 = (Name("scalafix-core"), Jvm, Scala.`2.13`)
      val artifact2 = (Name("scalafix"), Jvm, Scala.`2.13`)
      val res = ArtifactPages.getDefaultArtifact(Seq(artifact1, artifact2))
      assert(res == artifact2._1)
    }
  }

}
