package scaladex.core.model

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.ArtifactDependency.Scope

class ArtifactDependencyTests extends AnyFunSpec with Matchers {
  describe("ordering") {
    it("should order correctly 1") {
      val dep1 = dep(Artifact.Reference.from("org.specs2", "specs2-junit", "4.8.3"), Scope("test"))
      val dep2 = dep(Artifact.Reference.from("org.scala-lang", "scala3-library_3", "3.0.0"), Scope("compile"))
      Seq(dep1, dep2).sorted shouldBe Seq(dep2, dep1)
    }
    it("should order correctly 2") {
      val dep1 = dep(Artifact.Reference.from("org.scala-lang", "scala3-library_3", "3.0.0"), Scope("test"))
      val dep2 = dep(Artifact.Reference.from("org.scala-lang", "scala3-library_3", "3.0.0"), Scope("provided"))
      val dep3 = dep(Artifact.Reference.from("org.scala-lang", "scala3-library_3", "3.0.0"), Scope("compile"))
      Seq(dep1, dep2, dep3).sorted shouldBe Seq(dep3, dep2, dep1)
    }
    it("should order correctly 3") {
      val dep1 = dep(Artifact.Reference.from("org.scala-lang", "scala3-library_3", "3.0.0"), Scope("compile"))
      val dep2 = dep(Artifact.Reference.from("a", "specs2-junit", "4.8.3"), Scope("compile"))
      Seq(dep1, dep2).sorted shouldBe Seq(dep2, dep1)
    }
  }

  private val source = Artifact.Reference.from("org.typelevel", "cats-core_3", "2.6.1")
  private def dep(target: Artifact.Reference, scope: Scope): ArtifactDependency.Direct =
    ArtifactDependency.Direct(ArtifactDependency(source, target, scope), None)
}
