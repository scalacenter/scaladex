package ch.epfl.scala.index.data.elastic

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Using

import akka.actor.ActorSystem
import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.PomsReader
import ch.epfl.scala.index.data.project._
import ch.epfl.scala.index.search.DataRepository
import com.typesafe.scalalogging.LazyLogging

class SeedElasticSearch(
    paths: DataPaths,
    githubDownload: GithubDownload,
    dataRepository: DataRepository
)(implicit
    val ec: ExecutionContext
) extends LazyLogging {

  def run(): Unit = {

    val resetIndex = for {
      _ <- dataRepository.deleteAll()
      _ = logger.info("creating index")
      _ <- dataRepository.create()
    } yield ()

    Await.result(resetIndex, Duration.Inf)

    logger.info("loading update data")
    val projectConverter = new ProjectConvert(paths, githubDownload)
    val allData = projectConverter.convertAll(PomsReader.loadAll(paths), Map())

    var count = 0
    allData.foreach { case (project, releases, dependencies) =>
      logger.info(s"indexing ${project.reference}")
      val indexProjectF = dataRepository.insertProject(project)
      val indexReleasesF = dataRepository.insertReleases(releases)
      val indexDependenciesF = dataRepository.insertDependencies(dependencies)

      val indexAll = for {
        _ <- indexProjectF
        releasesResult <- indexReleasesF
        dependenciesResult <- indexDependenciesF
      } yield {
        val failures =
          (releasesResult ++ dependenciesResult).filter(_.status >= 300)
        if (failures.nonEmpty) {
          logger.error(s"indexing projects ${project.reference} failed")
          failures.foreach(
            _.error.foreach(error => logger.error(error.reason))
          )
        }
      }
      Await.result(indexAll, Duration.Inf)
      count += 1
    }
    logger.info(s"$count projects indexed")
  }

}

object SeedElasticSearch {
  def run(dataPaths: DataPaths)(implicit sys: ActorSystem): Unit = {
    import sys.dispatcher
    Using.resource(DataRepository.open()) { dataRepository =>
      val githubDownload = new GithubDownload(dataPaths)
      val seed =
        new SeedElasticSearch(dataPaths, githubDownload, dataRepository)
      seed.run()
    }
  }
}
