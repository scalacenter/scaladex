package scaladex.infra

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class QueryDocumentFormatTests extends AnyFunSpec with Matchers {

  val queryFormatter: QueryDocumentFormat = new QueryDocumentFormat {}

  describe("fieldAccess with no default argument") {
    it("should evaluate to field access syntax of the given field") {
      val field = "githubInfo.stars"
      val accessExpr = queryFormatter.fieldAccess(field)
      accessExpr shouldBe "doc['githubInfo.stars'].value"
    }
  }

  describe("fieldAccess with a default argument") {
    it("should evaluate to a field access that checks for nullability, and provides a default value") {
      val field = "githubInfo.stars"
      val accessExpr = queryFormatter.fieldAccess(field, default = "0")
      accessExpr shouldBe "doc['githubInfo.stars'].value != null ? doc['githubInfo.stars'].value : 0"
    }
  }
}
