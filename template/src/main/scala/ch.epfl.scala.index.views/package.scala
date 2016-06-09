package ch.epfl.scala.index
package views

import model.Url

package object html {
  def paginationRender(current: Int, total: Int, window: Int = 10): (Option[Int], List[Int], Option[Int]) = {
    
    val prev =
      if(current == 1) None
      else Some(current -1)

    val next =
      if(current == total) None
      else Some(current + 1)
        
    val delta = (window - 1) / 2
    val (start, end) =
      if(current + delta <= total) {
        if(current - delta >= 1) (current - delta, current + delta)
        else (1, window + 1)
      } else {
        if(total < window) (1, total)
        else (total - window, total)
      }

    val sels = (start to end).toList

    (prev, sels, next)
  }
}