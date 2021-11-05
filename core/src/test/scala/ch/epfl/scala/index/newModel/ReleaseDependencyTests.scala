package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.release.MavenReference
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ReleaseDependencyTests extends AnyFunSpec with Matchers {
  type Scope = String
  val releaseRef: MavenReference =
    MavenReference("org.typelevel", "cats-core_3", "2.6.1")
  describe("ordering") {
    it("should order correctly 1") {
      val ref1 = (MavenReference("org.specs2", "specs2-junit", "4.8.3"), "test")
      val ref2 = (
        MavenReference("org.scala-lang", "scala3-library_3", "3.0.0"),
        "compile"
      )
      val dependencies = getFulldependencies(Seq(ref1, ref2))
      dependencies.sorted shouldBe getFulldependencies(Seq(ref2, ref1))
    }
    it("should order correctly 2") {
      val ref1 =
        (MavenReference("org.scala-lang", "scala3-library_3", "3.0.0"), "test")
      val ref2 = (
        MavenReference("org.scala-lang", "scala3-library_3", "3.0.0"),
        "provided"
      )
      val ref3 = (
        MavenReference("org.scala-lang", "scala3-library_3", "3.0.0"),
        "compile"
      )
      val dependencies = getFulldependencies(Seq(ref1, ref2, ref3))
      dependencies.sorted shouldBe getFulldependencies(Seq(ref3, ref2, ref1))
    }
    it("should order correctly 3") {
      val ref1 = (
        MavenReference("org.scala-lang", "scala3-library_3", "3.0.0"),
        "compile"
      )
      val ref2 = (MavenReference("a", "specs2-junit", "4.8.3"), "compile")
      val dependencies = getFulldependencies(Seq(ref1, ref2))
      dependencies.sorted shouldBe getFulldependencies(Seq(ref2, ref1))
    }
  }

  private def getFulldependencies(
      refs: Seq[(MavenReference, Scope)]
  ): Seq[ReleaseDependency.Direct] =
    refs.map {
      case (ref, scope) =>
        ReleaseDependency.Direct(ReleaseDependency(releaseRef, ref, scope), None)
    }

}
