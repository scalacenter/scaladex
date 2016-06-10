package ch.epfl.scala.index
package views

import model.{Url, Project, Descending}

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

  def latestRelease(project: Project): String = {
    import com.github.nscala_time.time.Imports._
    import org.joda.time.format.ISODateTimeFormat
    import org.joda.time.format.DateTimeFormat

    val format = DateTimeFormat.forPattern("dd/MM/yyyy")

    val parser = ISODateTimeFormat.dateTime.withOffsetParsed

    val dates =
      for {
        artifact <- project.artifacts
        release  <- artifact.releases
        date     <- release.releaseDates
      } yield parser.parseDateTime(date.value)

    dates.sorted(Descending[org.joda.time.DateTime]).headOption.map(format.print).getOrElse("")
  }
}