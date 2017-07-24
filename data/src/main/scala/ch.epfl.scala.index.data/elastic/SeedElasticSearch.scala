package ch.epfl.scala.index
package data
package elastic

import project._
import github.GithubDownload
import maven.PomsReader

import com.sksamuel.elastic4s.ElasticDsl._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

import org.slf4j.LoggerFactory

class SeedElasticSearch(paths: DataPaths, githubDownload: GithubDownload)(
    implicit val ec: ExecutionContext
) extends ProjectProtocol {

  private val log = LoggerFactory.getLogger(getClass)

  def run(): Unit = {

    val exists = Await
      .result(esClient.execute { indexExists(indexName) }, Duration.Inf)
      .isExists()

    if (exists) {
      Await.result(esClient.execute {
        deleteIndex(indexName)
      }, Duration.Inf)
    }

    val projectFields = List(
      keywordField("organization"),
      keywordField("repository"),
      keywordField("defaultArtifact").index(false),
      keywordField("artifacts"),
      keywordField("customScalaDoc").index(false),
      keywordField("artifactDeprecations").index(false),
      keywordField("cliArtifacts").index(false),
      keywordField("targets"),
      keywordField("dependencies"),
      objectField("github").fields(keywordField("topics"),
                                   nestedField("beginnerIssues")),
      dateField("created"),
      dateField("updated")
    )

    val releasesFields = List(
      nestedField("reference")
        .fields(
          keywordField("organization"),
          keywordField("repository"),
          keywordField("artifact")
        )
        .includeInAll(true),
      nestedField("maven").fields(
        keywordField("groupId"),
        keywordField("artifactId"),
        keywordField("version")
      ),
      keywordField("version"),
      keywordField("targetType"),
      keywordField("scalaVersion"),
      keywordField("scalaJsVersion"),
      keywordField("scalaNativeVersion"),
      dateField("released")
    )

    log.info("creating index")
    Await.result(
      esClient.execute {
        createIndex(indexName)
          .mappings(
            mapping(projectsCollection).fields(projectFields: _*),
            mapping(releasesCollection).fields(releasesFields: _*)
          )
      },
      Duration.Inf
    )

    log.info("loading update data")
    val projectConverter = new ProjectConvert(paths, githubDownload)
    val newData = projectConverter(
      PomsReader.loadAll(paths).collect {
        case Success(pomAndMeta) => pomAndMeta
      }
    )

    val (projects, projectReleases) = newData.unzip
    val releases = projectReleases.flatten

    val progress = ProgressBar("Indexing releases", releases.size, log)
    progress.start()
    val bunch = 1000
    releases.grouped(bunch).foreach { group =>
      val bulkResults = Await.result(esClient.execute {
        bulk(
          group.map(
            release =>
              indexInto(indexName / releasesCollection).source(release)
          )
        )
      }, Duration.Inf)

      if (bulkResults.hasFailures) {
        bulkResults.failures.foreach(p => log.error(p.failureMessage))
        log.error("Indexing releases failed")
      }

      progress.stepBy(bunch)
    }
    progress.stop()

    val bunch2 = 100
    log.info(s"Indexing projects (${projects.size})")
    projects.grouped(bunch2).foreach { group =>
      val bulkResults = Await.result(esClient.execute {
        bulk(
          group.map(
            project =>
              indexInto(indexName / projectsCollection).source(project)
          )
        )
      }, Duration.Inf)

      if (bulkResults.hasFailures) {
        bulkResults.failures.foreach(p => log.error(p.failureMessage))
        log.error("Indexing projects failed")
      }
    }

    ()
  }
}
