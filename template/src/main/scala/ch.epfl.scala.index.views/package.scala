package ch.epfl.scala.index
package views

import model._

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

  def mainArtifact(project: Project) = {
    project.artifacts.filter(_.reference == project.reference).headOption match {
      case Some(v) => Some(v)
      case None => project.artifacts.headOption
    }
  }

  def formatDate(date: ISO_8601_Date): String = {
    import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
    val in = ISODateTimeFormat.dateTime.withOffsetParsed
    val out = DateTimeFormat.forPattern("dd/MM/yyyy");

    out.print(in.parseDateTime(date.value))
  }
}