package ch.epfl.scala.index
package server.routes.api

import org.scalatest.FunSpec
import org.scalatest.Matchers

class SearchApiTests extends FunSpec with Matchers {
  describe("parseScalaTarget") {
    it("should not recognize 3.x.y") {
      val res =
        SearchApi.parseScalaTarget(Some("JVM"), Some("3.0.1"), None, None, None)
      assert(res.isEmpty)
    }

    it("should not recognize scala3") {
      val res = SearchApi.parseScalaTarget(
        Some("JVM"),
        Some("scala3"),
        None,
        None,
        None
      )
      assert(res.isEmpty)
    }

    it("should recognize JVM/3") {
      val res =
        SearchApi.parseScalaTarget(Some("JVM"), Some("3"), None, None, None)
      assert(res.map(_.languageVersion.render) == Some("scala 3"))
    }

  }
}
