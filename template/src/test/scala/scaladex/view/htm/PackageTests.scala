package scaladex.view.html

import org.scalatest.funspec.AsyncFunSpec

class PackageTests extends AsyncFunSpec {
  describe("pagination") {
    it("base case") {
      // *1*
      assert(
        paginationRender(1, 1) ==
          ((None, List(1), None))
      )
    }

    it("full") {
      // *1* 2 3 >
      assert(
        paginationRender(1, 3) ==
          ((None, List(1, 2, 3), Some(2)))
      )

      // *1* 2  3  4  5  6  7  8    9   10  >
      assert(
        paginationRender(1, 12) ==
          ((None, List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), Some(2)))
      )

      // < 1 *2* 3  4  5  6  7  8    9   10  >
      assert(
        paginationRender(2, 12) ==
          ((Some(1), List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), Some(3)))
      )

      // < 1  2  3  4  5 *6* 7  8    9   10  >
      assert(
        paginationRender(6, 12) ==
          ((Some(5), List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), Some(7)))
      )

      // < 2 3  4  5  6 *7* 8  9   10   11  >
      assert(
        paginationRender(7, 12) ==
          ((Some(6), List(2, 3, 4, 5, 6, 7, 8, 9, 10, 11), Some(8)))
      )

      // < 3  4  5  6  7 *8* 9  10  11   12  >
      assert(
        paginationRender(8, 12) ==
          ((Some(7), List(3, 4, 5, 6, 7, 8, 9, 10, 11, 12), Some(9)))
      )

      // < 3  4  5  6  7  8  9  10 *11*  12  >
      assert(
        paginationRender(11, 12) ==
          ((Some(10), List(3, 4, 5, 6, 7, 8, 9, 10, 11, 12), Some(12)))
      )

      // < 3  4  5  6  7  8  9  10  11  *12*
      assert(
        paginationRender(12, 12) ==
          ((Some(11), List(3, 4, 5, 6, 7, 8, 9, 10, 11, 12), None))
      )
    }
  }
}
