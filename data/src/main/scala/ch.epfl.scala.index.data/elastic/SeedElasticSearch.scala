package ch.epfl.scala.index.data.elastic

import akka.actor.ActorSystem
import akka.stream.Materializer

import build.info.BuildInfo

import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.PomsReader
import ch.epfl.scala.index.data.ProgressBar
import ch.epfl.scala.index.data.project._
import ch.epfl.scala.index.search.DataRepository

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

class SeedElasticSearch(
    paths: DataPaths,
    githubDownload: GithubDownload,
    dataRepository: DataRepository
)(
    implicit val ec: ExecutionContext
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
    val newData = projectConverter(
      PomsReader.loadAll(paths).collect {
        case Success(pomAndMeta) => pomAndMeta
      }
    )

    val (projects, projectReleases) = newData.unzip
    val releases = projectReleases.flatten

    val progress =
      ProgressBar("Indexing releases", releases.size, logger.underlying)
    progress.start()
    val bunch = 1000
    releases.grouped(bunch).foreach { group =>
      val bulkResults = Await.result(
        dataRepository.insertReleases(group),
        Duration.Inf
      )

      if (bulkResults.hasFailures) {
        bulkResults.failures.foreach(p => logger.error(p.failureMessage))
        logger.error("Indexing releases failed")
      }

      progress.stepBy(bunch)
    }
    progress.stop()

    val bunch2 = 100
    logger.info(s"Indexing projects (${projects.size})")
    projects.grouped(bunch2).foreach { group =>
      val bulkResults = Await.result(
        dataRepository.insertProjects(group),
        Duration.Inf
      )

      if (bulkResults.hasFailures) {
        bulkResults.failures.foreach(p => logger.error(p.failureMessage))
        logger.error("Indexing projects failed")
      }
    }
  }
}

object SeedElasticSearch {
  def run(dataPaths: DataPaths)(implicit sys: ActorSystem,
                                mat: Materializer): Unit = {
    import sys.dispatcher
    for (dataRepository <- DataRepository.open(BuildInfo.baseDirectory)) {
      val githubDownload = new GithubDownload(dataPaths)
      val seed =
        new SeedElasticSearch(dataPaths, githubDownload, dataRepository)
      seed.run()
    }
  }
}
