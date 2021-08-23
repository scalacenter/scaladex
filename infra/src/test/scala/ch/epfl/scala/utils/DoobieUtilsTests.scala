package ch.epfl.scala.utils

import ch.epfl.scala.utils.DoobieUtils.Fragments._
import doobie.implicits._
import doobie.util.fragment.Fragment
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class DoobieUtilsTests extends AnyFunSpec with Matchers {
  describe("buildInsertOrUpdate") {
    it("should build correctly the insert or update query") {
      val table = Fragment.const0("testTable")
      val fields = Fragment.const0("id, name, description")
      val id = "oneId"
      val name = "oneName"
      val description = "oneDescription"
      val values = fr0"$id, $name, $description"
      buildInsertOrUpdate(table, fields, values, fr0"id", fr0"NOTHING")
        .toString() shouldBe
        """Fragment("INSERT INTO testTable (id, name, description) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING")"""
    }
  }
}
