package ch.epfl.scala.index
package views
import utest._

object PaginationTests extends TestSuite{
  val tests = this{
    "pagination"-{
      "base case"-{
        paginationRender(1,1,1) ==>
          (None, List(1), None)
      }
    }
  }
}