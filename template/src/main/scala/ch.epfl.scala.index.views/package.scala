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
        if(current - delta-1 >= 1) (current - delta-1, current + delta)
        else (1, window)
      } else {
        if(total <= window) (1, total)
        else (total - window, total)
      }

    val sels = (start to end).toList

    (prev, sels, next)
  }

  // /**
  //  * Get the main artifact
  //  * - todo: default selected artifact
  //  * - first check if there is -core artifact
  //  * - check if name == organization
  //  * - take the first one
  //  * @param project the project to observe
  //  * @return
  //  */
  // def mainArtifact(project: Project) = {

  //   List(
  //     //todo project default: project.artifact.find(_.reference.name == project.defaultArtifact) 
  //     project.artifacts.find(_.reference.name.endsWith("-core")),
  //     project.artifacts.find(r => r.reference.organization == r.reference.name),
  //     project.artifacts.headOption
  //   ).find(_.nonEmpty).flatten
  // }

  def formatDate(date: String): String = {
    import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
    val in = ISODateTimeFormat.dateTime.withOffsetParsed
    val out = DateTimeFormat.forPattern("dd/MM/yyyy")

    out.print(in.parseDateTime(date))
  }
}