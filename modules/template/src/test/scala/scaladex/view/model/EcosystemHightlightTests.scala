package scaladex.view.model

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model._

class EcosystemHighlightTest extends AnyFunSpec with Matchers {
  it("ordering") {
    val `1.x` = EcosystemVersion(Version(1), 0, Url(""))
    val `0.13` = EcosystemVersion(Version(0, 13), 0, Url(""))
    val `2.0.0-M2` =
      EcosystemVersion(Version.SemanticLike(2, Some(0), Some(0), preRelease = Some(Milestone(2))), 0, Url(""))

    val highlight = EcosystemHighlight("sbt", Seq(`1.x`, `0.13`, `2.0.0-M2`)).get
    highlight.currentVersion shouldBe `1.x`
    (highlight.otherVersions should contain).theSameElementsInOrderAs(Seq(`0.13`, `2.0.0-M2`))
  }
}
