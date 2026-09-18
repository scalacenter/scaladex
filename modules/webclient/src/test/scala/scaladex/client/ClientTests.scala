package scaladex.client

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class ClientTests extends AnyFunSpec with Matchers with TableDrivenPropertyChecks:
  private val root = "https://github.com/scalacenter/scaladex"
  private val raw = "https://github.com/scalacenter/scaladex/raw/main"
  private val blob = "https://github.com/scalacenter/scaladex/blob/main"

  describe("resolveReadmeLinkUrl") {
    it("rewrites a relative image src against the raw content root") {
      val cases = Table(
        ("attrValue", "expected"),
        ("logo.png", s"$raw/logo.png"),
        ("/assets/logo.png", s"$raw/assets/logo.png")
      )
      forAll(cases) { (attrValue, expected) =>
        Client.resolveReadmeLinkUrl(isAnchor = false, attrValue, root, raw, blob) shouldBe (("src", expected))
      }
    }

    it("rewrites a relative anchor href against the blob view root") {
      val cases = Table(
        ("attrValue", "expected"),
        ("CONTRIBUTING.md", s"$blob/CONTRIBUTING.md"),
        ("/docs/setup.md", s"$blob/docs/setup.md")
      )
      forAll(cases) { (attrValue, expected) =>
        Client.resolveReadmeLinkUrl(isAnchor = true, attrValue, root, raw, blob) shouldBe (("href", expected))
      }
    }

    it("rewrites an in-page anchor href against the repository root instead of the blob view") {
      Client.resolveReadmeLinkUrl(isAnchor = true, "#installation", root, raw, blob) shouldBe
        (("href", s"$root/#installation"))
    }
  }
end ClientTests
