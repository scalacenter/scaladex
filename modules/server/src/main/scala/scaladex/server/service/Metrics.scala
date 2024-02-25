package scaladex.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import scaladex.infra.SqlDatabase
import scaladex.core.model.Scala

class Metrics(db: SqlDatabase)(implicit ec: ExecutionContext) extends LazyLogging {
  def run(): Future[String] = {
    val years: Seq[Int] = Range.inclusive(2013, 2022)
    for {
      projectsByYear <- years.traverse(db.getProjectsByYear)
      projects <- db.getAllProjects()
    } yield {
      val projectMap = projects.map(p => p.reference -> p).toMap
      years.zip(projectsByYear).foreach { case (year, projects) =>
        logger.info(s"$year:")
        val all = projects
          .groupMap(_._1)(_._2)
          .view
          .filterKeys(k => projectMap.get(k).filter(p => !p.githubStatus.isMoved && !p.githubStatus.isNotFound).nonEmpty)
          .values.map(_.toSet).toSeq
        val scala3  = all.count(_.contains(Scala.`3`))
        val scala213  = all.count(ls => ls.contains(Scala.`2.13`) && !ls.contains(Scala.`3`))
        val scala212 = all.count(ls => ls.contains(Scala.`2.12`) && !ls.contains(Scala.`2.13`) && !ls.contains(Scala.`3`))
        val scala211 = all.count(ls => ls.contains(Scala.`2.11`) && !ls.contains(Scala.`2.12`) && !ls.contains(Scala.`2.13`) && !ls.contains(Scala.`3`))
        val scala210 = all.count(ls => ls.contains(Scala.`2.10`) && !ls.contains(Scala.`2.11`) && !ls.contains(Scala.`2.12`) && !ls.contains(Scala.`2.13`) && !ls.contains(Scala.`3`))
        logger.info(s"  Scala 2.10: $scala210")
        logger.info(s"  Scala 2.11: $scala211")
        logger.info(s"  Scala 2.12: $scala212")
        logger.info(s"  Scala 2.13: $scala213")
        logger.info(s"  Scala 3: $scala3")
      }
      val filteredProjects = projects.filter(p => !p.githubStatus.isMoved && !p.githubStatus.isNotFound)
      logger.info(s"total projects: ${filteredProjects.size}")
      val contributors = filteredProjects.flatMap(_.githubInfo).flatMap(_.contributors).map(_.login).distinct.size
      logger.info(s"total contributors: $contributors")
      "Success"
    }
  }
}
