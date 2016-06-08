package ch.epfl.scala.index
package views

import model.Url

package object html {
  def trimProtocol(url: Url): String = {
    val u = url.target
    val http = "http://"
    val https = "https://"

    if(u.startsWith(http)) u.drop(http.length)
    else if(u.startsWith(https)) u.drop(https.length)
    else u
  }

  def pagination(current: Int, total: Int, window: Int): (Option[Int], List[Int], Option[Int]) = {
    
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
      } else (total - window, total)

    val sels = (start to end).toList

    (prev, sels, next)
  }
}