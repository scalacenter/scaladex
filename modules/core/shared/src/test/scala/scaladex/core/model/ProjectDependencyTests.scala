package scaladex.core.model

import scaladex.core.model.ArtifactDependency.Scope

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectDependencyTests extends AnyFunSpec with Matchers:
  val us: Project.Reference = Project.Reference.from("typelevel", "cats")
  def dep(org: String, repo: String, sourceV: String, targetV: String, scope: String): ProjectDependency =
    ProjectDependency(Project.Reference.from(org, repo), Version(sourceV), us, Version(targetV), Scope(scope))

  describe("collapseByProject") {
    it("keeps one row per project on the chosen side") {
      val rows = Seq(
        dep("org", "a", "1.0.0", "2.0.0", "test"),
        dep("org", "a", "1.1.0", "2.1.0", "compile"),
        dep("org", "b", "3.0.0", "2.0.0", "compile")
      )
      val collapsed = ProjectDependency.collapseByProject(rows, _.source)
      collapsed.map(_.source.repository.value) shouldBe Seq("a", "b")
    }

    it("keeps the lowest scope and the highest versions of the group") {
      val rows = Seq(
        dep("org", "a", "1.0.0", "2.0.0", "test"),
        dep("org", "a", "1.2.0", "2.5.0", "compile")
      )
      val Seq(collapsed) = ProjectDependency.collapseByProject(rows, _.source)
      collapsed.scope shouldBe Scope("compile")
      collapsed.sourceVersion shouldBe Version("1.2.0")
      collapsed.targetVersion shouldBe Version("2.5.0")
    }

    it("sorts by organization then repository") {
      val rows = Seq(
        dep("z-org", "a", "1.0.0", "1.0.0", "compile"),
        dep("a-org", "z", "1.0.0", "1.0.0", "compile"),
        dep("a-org", "a", "1.0.0", "1.0.0", "compile")
      )
      ProjectDependency.collapseByProject(rows, _.source).map(d => d.source.toString) shouldBe
        Seq("a-org/a", "a-org/z", "z-org/a")
    }
  }
end ProjectDependencyTests
