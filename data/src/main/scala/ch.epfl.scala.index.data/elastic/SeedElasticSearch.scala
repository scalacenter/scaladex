package ch.epfl.scala.index
package data
package elastic

import project._
import maven.PomsReader

import me.tongfei.progressbar._

import com.sksamuel.elastic4s.ElasticDsl._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

class SeedElasticSearch(paths: DataPaths)(implicit val ec: ExecutionContext)
    extends ProjectProtocol {
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
      nestedField("github").fields(
        keywordField("topics")
      ),
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

    println("creating index")
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

    println("loading update data")
    val projectConverter = new ProjectConvert(paths)
    val newData = projectConverter(
      PomsReader.loadAll(paths).collect {
        case Success(pomAndMeta) => pomAndMeta
      }
    )

    val (projects, projectReleases) = newData.unzip
    val releases = projectReleases.flatten

    val progress = new ProgressBar("Indexing releases", releases.size)
    progress.start()
    val bunch = 1000
    releases.grouped(bunch).foreach { group =>
      Await.result(esClient.execute {
        bulk(group.map(release =>
          indexInto(indexName / releasesCollection).source(release)))
      }, Duration.Inf)
      progress.stepBy(bunch)
    }

    val bunch2 = 100
    println(s"Indexing projects (${projects.size})")
    projects.grouped(bunch2).foreach { group =>
      Await.result(esClient.execute {
        bulk(group.map(project =>
          indexInto(indexName / projectsCollection).source(project)))
      }, Duration.Inf)
    }

    ()
  }
}
