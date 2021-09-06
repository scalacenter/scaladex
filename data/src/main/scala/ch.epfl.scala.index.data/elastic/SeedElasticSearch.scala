package ch.epfl.scala.index.data.elastic

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.search.ESRepo
import com.sksamuel.elastic4s.requests.bulk.BulkResponseItem
import com.typesafe.scalalogging.LazyLogging

class SeedElasticSearch(
    dataRepository: ESRepo
)(implicit
    val ec: ExecutionContext
) extends LazyLogging {

  def cleanIndexes(): Future[Unit] = {
    for {
      _ <- dataRepository.deleteAll()
      _ = logger.info("creating index")
      _ <- dataRepository.create()
    } yield ()
  }

  def insertES(
      projects: Seq[Project],
      releases: Seq[Release]
  ): Future[Unit] = {
    val projectF = dataRepository.insertProjects(projects)
    val releasesF = dataRepository.insertReleases(releases)
    for {
      projectResult <- projectF
      releasesResult <- releasesF
    } yield {
      logES(projectResult, "projects")
      logES(releasesResult, "releases")
    }
  }
  private def logES(res: Seq[BulkResponseItem], name: String): Unit = {
    val (resFailures, resSuccess) = res.partition(_.status >= 300)
    if (resFailures.nonEmpty) {
      resFailures.foreach(
        _.error.foreach(error => logger.error(error.reason))
      )
    }
    logger.info(
      s"${resSuccess.size} $name have been inserted into ElasticSearch"
    )
  }
}
