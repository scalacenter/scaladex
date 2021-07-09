package ch.epfl.scala.index.data.elastic

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.model.release.ScalaDependency
import ch.epfl.scala.index.search.ESRepo
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
      project: Project,
      releases: Seq[Release],
      dependencies: Seq[ScalaDependency]
  ): Future[Project.Reference] = {
    logger.info(s"indexing ${project.reference}")
    val projectF = dataRepository.insertProject(project)
    val releasesF = dataRepository.insertReleases(releases)
    val dependenciesF = dataRepository.insertDependencies(dependencies)
    for {
      _ <- projectF
      releasesResult <- releasesF
      dependenciesResult <- dependenciesF
    } yield {
      val failures =
        (releasesResult ++ dependenciesResult).filter(_.status >= 300)
      if (failures.nonEmpty) {
        logger.error(s"indexing projects ${project.reference} failed")
        failures.foreach(
          _.error.foreach(error => logger.error(error.reason))
        )
      }
      project.reference
    }
  }

}
