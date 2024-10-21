package scaladex.core.model

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.ArtifactDependency.Scope

class ArtifactDependencyTests extends AnyFunSpec with Matchers {
  describe("ordering") {
    it("should order correctly 1") {
      val ref1 = (Artifact.Reference.unsafe("org.specs2", "specs2-junit", "4.8.3"), Scope("test"))
      val ref2 = (
        Artifact.Reference.unsafe("org.scala-lang", "scala3-library_3", "3.0.0"),
        Scope("compile")
      )
      val dependencies = getFulldependencies(Seq(ref1, ref2))
      dependencies.sorted shouldBe getFulldependencies(Seq(ref2, ref1))
    }
    it("should order correctly 2") {
      val ref1 =
        (Artifact.Reference.unsafe("org.scala-lang", "scala3-library_3", "3.0.0"), Scope("test"))
      val ref2 = (
        Artifact.Reference.unsafe("org.scala-lang", "scala3-library_3", "3.0.0"),
        Scope("provided")
      )
      val ref3 = (
        Artifact.Reference.unsafe("org.scala-lang", "scala3-library_3", "3.0.0"),
        Scope("compile")
      )
      val dependencies = getFulldependencies(Seq(ref1, ref2, ref3))
      dependencies.sorted shouldBe getFulldependencies(Seq(ref3, ref2, ref1))
    }
    it("should order correctly 3") {
      val ref1 = (
        Artifact.Reference.unsafe("org.scala-lang", "scala3-library_3", "3.0.0"),
        Scope("compile")
      )
      val ref2 = (Artifact.Reference.unsafe("a", "specs2-junit", "4.8.3"), Scope("compile"))
      val dependencies = getFulldependencies(Seq(ref1, ref2))
      dependencies.sorted shouldBe getFulldependencies(Seq(ref2, ref1))
    }
  }

  private def getFulldependencies(
      targets: Seq[(Artifact.Reference, Scope)]
  ): Seq[ArtifactDependency.Direct] =
    targets.map {
      case (target, scope) =>
        val source = Artifact.Reference.unsafe("org.typelevel", "cats-core_3", "2.6.1")
        ArtifactDependency.Direct(ArtifactDependency(source, target, scope), None)
    }

}
