package ch.epfl.scala.index
package views
package html

object PaginationTests extends org.specs2.mutable.Specification {
  "pagination" >> {
    "base case" >> {
      // *1*
      paginationRender(1, 1) ==== ((None, List(1), None))
    }
    "full" >> {
      // *1* 2 3 >
      paginationRender(1, 3) ==== ((None, List(1, 2, 3), Some(2)))

      // *1* 2  3  4  5  6  7  8    9   10  >
      paginationRender(1, 12) ==== ((None, List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), Some(2)))

      // < 1 *2* 3  4  5  6  7  8    9   10  >
      paginationRender(2, 12) ==== ((Some(1), List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), Some(3)))
      
      // < 1  2  3  4  5 *6* 7  8    9   10  >
      paginationRender(6, 12) ==== ((Some(5), List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), Some(7)))
      
      // < 2 3  4  5  6 *7* 8  9   10   11  >
      paginationRender(7, 12) ==== ((Some(6), List(2, 3, 4, 5, 6, 7, 8, 9, 10, 11), Some(8)))

      // < 3  4  5  6  7 *8* 9  10  11   12  >
      paginationRender(8, 12) ==== ((Some(7), List(3, 4, 5, 6, 7, 8, 9, 10, 11, 12), Some(9)))

      // < 3  4  5  6  7  8  9  10 *11*  12  >
      paginationRender(11, 12) ==== ((Some(10), List(3, 4, 5, 6, 7, 8, 9, 10, 11, 12), Some(12)))

      // < 3  4  5  6  7  8  9  10  11  *12*
      paginationRender(12, 12) ==== ((Some(11), List(3, 4, 5, 6, 7, 8, 9, 10, 11, 12), None))
    }
  }
}