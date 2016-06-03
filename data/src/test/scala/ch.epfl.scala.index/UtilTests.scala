package ch.epfl.scala.index
package data
package project

import utest._

object UtilTest extends TestSuite{
  private val empty = Map.empty[Symbol, String]

  val tests = this{
    "innerJoin"-{
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
      "example"-{
        innerJoin(left, right)((a, b) => (a, b)) ==>
          Map('a -> (("la", "ra")), 'b -> (("lb", "rb")), 'c -> (("lc", "rc")))
      }
      "discard missing left keys"-{
        innerJoin(left - 'a, right)((_, _)) ==>
          Map('b -> (("lb", "rb")), 'c -> (("lc", "rc")))
      }
      "discard missing right keys"-{
        innerJoin(left, right - 'a)((_, _)) ==>
          Map('b -> (("lb", "rb")), 'c -> (("lc", "rc")))
      }
      "left empty"-{
        innerJoin(empty, right)(_ + _) ==> empty
      }
      "right empty"-{
        innerJoin(left, empty)(_ + _) ==> empty
      }
      "empty empty"-{
        innerJoin(empty, empty)(_ + _) ==> empty
      }
    }
    "upsert"-{
      val ma = Map('a -> Set(1))
      "insert if key is not found"-{
        upsert(ma, 'b, 1) ==> Map('a -> Set(1), 'b -> Set(1))
      }
      "append if key is found"-{
        upsert(ma, 'a, 2) ==> Map('a -> Set(1, 2))
      }
    }
    "fullOuterJoin"-{
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
      "left empty"-{
        fullOuterJoin(empty, right)(_ + _)(l => l)(r => r) ==> right
      }
      "right empty"-{
        fullOuterJoin(left, empty)(_ + _)(l => l)(r => r) ==> left
      }
      "empty empty"-{
        fullOuterJoin(empty, empty)(_ + _)(l => l)(r => r) ==> empty
      }
      "example"-{
        fullOuterJoin(left, right)(_ + _)(l => l)(r => r) ==> Map(
          'a -> "la",
          'b -> "lbrb",
          'c -> "lcrc",
          'd -> "rd"
        )
      }
    }
  }
}