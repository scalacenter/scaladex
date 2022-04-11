package scaladex.server.service

import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scaladex.infra.SqlDatabase
import cats.implicits.toTraverseOps

class Metrics(db: SqlDatabase)(implicit ec: ExecutionContext) extends LazyLogging {
  def run(): Future[Unit] = {
    val years: Seq[Int] = Range.inclusive(2013, 2022)
    for {
      countByYear <- years.traverse(db.countProjects)
      projects <- db.getAllProjects()
    } yield {
      years.zip(countByYear).foreach {
        case (year, count) => logger.info(s"$year: $count")
      }
      val filteredProjects = projects.filter(p => !p.githubStatus.moved && !p.githubStatus.notFound)
      logger.info(s"total projects: ${filteredProjects.size}")
      val linesOfCode = filteredProjects.flatMap(_.githubInfo).flatMap(_.scalaLines).map(_.toLong).sum
      logger.info(s"total lines of Scala code: $linesOfCode")
      val contributors = filteredProjects.flatMap(_.githubInfo).flatMap(_.contributors).map(_.login).distinct.size
      logger.info(s"total contributors: $contributors")
    }
  }
}
