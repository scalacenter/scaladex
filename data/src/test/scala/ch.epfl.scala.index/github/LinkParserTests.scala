//package ch.epfl.scala.index
//package data
//package github
//
//import org.scalatest.funspec.AsyncFunSpec
//
//class LinkParserTests extends AsyncFunSpec {
//  describe("Link parser") {
//    it("quote") {
//      assert(
//        extractLastPage(
//          List(
//            """<https://api.github.com/repositories/130013/issues?page=2>; rel="next"""",
//            """<https://api.github.com/repositories/130013/issues?page=23>; rel="last""""
//          ).mkString(", ")
//        ) == 23
//      )
//    }
//    it("unquote") {
//      assert(
//        extractLastPage(
//          List(
//            """<https://api.github.com/user/repos?page=2>; rel=next""",
//            """<https://api.github.com/user/repos?page=2>; rel=last"""
//          ).mkString(", ")
//        ) == 2
//      )
//    }
//  }
//}
