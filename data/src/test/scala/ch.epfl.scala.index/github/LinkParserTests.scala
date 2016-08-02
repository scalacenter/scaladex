package ch.epfl.scala.index
package data
package github

object LinkParserTests extends org.specs2.mutable.Specification {
  "Link parser" >> {
    "quote" >> {
      extractLastPage(
        List(
          """<https://api.github.com/repositories/130013/issues?page=2>; rel="next"""",
          """<https://api.github.com/repositories/130013/issues?page=23>; rel="last""""
        ).mkString(", ")
      ) ==== 23
    }
    "unquote" >> {
      extractLastPage(
        List(
          """<https://api.github.com/user/repos?page=2>; rel=next""",
          """<https://api.github.com/user/repos?page=2>; rel=last"""
        ).mkString(", ")
      ) ==== 2
    }
  }
}