package ch.epfl.scala.index
package views

import model._

package object html {
  // https://www.reddit.com/r/scala/comments/4n73zz/scala_puzzle_gooooooogle_pagination/d41jor5
  def paginationRender(selected: Int, max: Int, toShow: Int = 10): (Option[Int], List[Int], Option[Int]) = {
    if(selected == max && max == 1) (None, List(1), None)
    else {
      val min = 1
      require(min to max contains selected, "cannot select something outside the range")
      require(min <= max, "min must not be greater than max")
      require(max > 0 && selected > 0 && toShow > 0, "all arguments must be positive")

      val window = (max min toShow) / 2
      val left = selected - window
      val right = selected + window

      val (minToShow, maxToShow) =
        if(max < toShow) (min, max)
        else {
          (left, right) match {
            case (l, r) if l < min => (min, min + toShow - 1)
            case (l, r) if r > max => (max - toShow + 1, max)
            case (l, r) => (l, r - 1 + toShow % 2)
          }
        }

      val prev =
        if(selected == 1) None
        else Some(selected -1)

      val next =
        if(selected == max) None
        else Some(selected + 1)

      (prev, (minToShow to maxToShow).toList, next)
    }
  }

  def formatDate(date: String): String = {
    import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
    val in = ISODateTimeFormat.dateTime.withOffsetParsed
    val out = DateTimeFormat.forPattern("dd/MM/yyyy")

    out.print(in.parseDateTime(date))
  }
}