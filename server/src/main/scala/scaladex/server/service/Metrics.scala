package scaladex.server.service

import ch.epfl.scala.services.storage.sql.SqlRepo
import com.typesafe.scalalogging.LazyLogging
import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import ch.epfl.scala.services.storage.sql.tables.ReleaseTable

class Metrics(db: SqlRepo)(implicit ec: ExecutionContext) extends LazyLogging {
  def run(): Future[Unit] = {
    logger.info(ReleaseTable.countProjects("3").sql)
    def global = for {
      artifacts <- db.countDistinctArtifacts()
      releases <- db.countReleases()
      projects <- db.countProjects()
      githubInfos <- db.getAllGithubInfos()
      scala210Projects <- db.countProjects("2.10")
      scala211Projects <- db.countProjects("2.11")
      scala212Projects <- db.countProjects("2.12")
      scala213Projects <- db.countProjects("2.13")
      scala3Projects <- db.countProjects("3")
    } yield {
      val contributors = githubInfos.flatMap(g => g.contributors).distinct.size
      logger.info(s"Total artifacts: $artifacts")
      logger.info(s"Total releases: $releases")
      logger.info(s"Total projects: $projects")
      logger.info(s"Total contributos: $contributors")
      logger.info(s"Scala 2.10 projects: $scala210Projects")
      logger.info(s"Scala 2.11 projects: $scala211Projects")
      logger.info(s"Scala 2.12 projects: $scala212Projects")
      logger.info(s"Scala 2.13 projects: $scala213Projects")
      logger.info(s"Scala 3 projects: $scala3Projects")
    }

    def yearly = {
      def instant(year: Int) = Instant.parse(s"$year-01-01T00:00:00.00Z")
      (2012 to 2021).foldLeft(Future.successful(())) { (f, year) =>
        val from = instant(year)
        val to = instant(year + 1)
        for {
          _ <- f
          projects <- db.countProjects(from, to)
        } yield {
          logger.info(s"New projects in $year: $projects")  
        }
      }
    }

    def monthly = {
      def instant(month:Int, year: Int) = Instant.parse(s"$year-${String.format("%02d", month)}-01T00:00:00.00Z")
      val months = for {
        year <- 2012 to 2021
        month <- 1 to 12
      } yield {
        val (nextYear, nextMonth) = if (month + 1 == 13) (year + 1, 1) else (year, month + 1)
        () => db.countReleases(instant(month, year), instant(nextMonth, nextYear)).map { releases =>
          logger.info(s"Releases in $year-${String.format("%02d", month)}: $releases")
        }
      }
      months.foldLeft(Future.successful(())) { (f, month) => f.flatMap(_=> month()) }
    }

    for {
      _ <- global
      _ <- yearly
      _ <- monthly
    } yield ()
  }
}
