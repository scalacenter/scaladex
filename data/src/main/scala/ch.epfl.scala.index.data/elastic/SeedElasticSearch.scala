package ch.epfl.scala.index.data.elastic

import akka.actor.ActorSystem

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
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.model.release.ScalaDependency
import scala.util.Using

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
        if (releasesResult.hasFailures || dependenciesResult.hasFailures) {
          logger.error(s"indexing projects ${project.reference} failed")
          releasesResult.failures.foreach(p => logger.error(p.failureMessage))
          dependenciesResult.failures.foreach(p =>
            logger.error(p.failureMessage)
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
    Using.resource(DataRepository.open(BuildInfo.baseDirectory)) {
      dataRepository =>
        val githubDownload = new GithubDownload(dataPaths)
        val seed =
          new SeedElasticSearch(dataPaths, githubDownload, dataRepository)
        seed.run()
    }
  }
}
