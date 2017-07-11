package ch.epfl.scala.index
package data
package project

import org.scalatest._

class UtilTests extends FunSpec {
  private val empty = Map.empty[Symbol, String]

  describe("innerJoin") {
    val left = Map(
      'a -> "la",
      'b -> "lb",
      'c -> "lc"
    )

    val right = Map(
      'a -> "ra",
      'b -> "rb",
      'c -> "rc"
    )

    it("example") {
      assert(
        innerJoin(left, right)((a, b) => (a, b)) ==
          Map('a -> (("la", "ra")), 'b -> (("lb", "rb")), 'c -> (("lc", "rc")))
      )
    }

    it("discard missing left keys") {
      assert(
        innerJoin(left - 'a, right)((_, _)) ==
          Map('b -> (("lb", "rb")), 'c -> (("lc", "rc")))
      )
    }

    it("discard missing right keys") {
      assert(
        innerJoin(left, right - 'a)((_, _)) ==
          Map('b -> (("lb", "rb")), 'c -> (("lc", "rc")))
      )
    }

    it("left empty") {
      assert(
        innerJoin(empty, right)(_ + _) == empty
      )
    }

    it("right empty") {
      assert(
        innerJoin(left, empty)(_ + _) == empty
      )
    }

    it("empty empty") {
      assert(
        innerJoin(empty, empty)(_ + _) == empty
      )
    }
  }

  describe("upsert") {
    val ma = Map('a -> Seq(1))

    it("insert if key is not found") {
      assert(
        upsert(ma, 'b, 1) ==
          Map('a -> Seq(1), 'b -> Seq(1))
      )
    }

    it("append if key is found") {
      assert(
        upsert(ma, 'a, 2) == Map('a -> Seq(1, 2))
      )
    }
  }

  describe("fullOuterJoin") {
    val left = Map(
      'a -> "la",
      'b -> "lb",
      'c -> "lc"
    )

    val right = Map(
      'b -> "rb",
      'c -> "rc",
      'd -> "rd"
    )
    it("left empty") {
      assert(
        fullOuterJoin(empty, right)(_ + _)(l => l)(r => r) == right
      )
    }

    it("right empty") {
      assert(
        fullOuterJoin(left, empty)(_ + _)(l => l)(r => r) == left
      )
    }

    it("empty empty") {
      assert(
        fullOuterJoin(empty, empty)(_ + _)(l => l)(r => r) == empty
      )
    }

    it("example") {
      assert(
        fullOuterJoin(left, right)(_ + _)(l => l)(r => r) == Map(
          'a -> "la",
          'b -> "lbrb",
          'c -> "lcrc",
          'd -> "rd"
        )
      )
    }
  }

}
