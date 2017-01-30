package ch.epfl.scala.index
package data
package elastic

import project._
import maven.PomsReader

import me.tongfei.progressbar._

import com.sksamuel.elastic4s._
import ElasticDsl._
import mappings.FieldType._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

class SeedElasticSearch(paths: DataPaths)(implicit val ec: ExecutionContext)
    extends ProjectProtocol {
  def run(): Unit = {

    val exists = Await.result(esClient.execute { indexExists(indexName) }, Duration.Inf).isExists()

    if (exists) {
      Await.result(esClient.execute {
        deleteIndex(indexName)
      }, Duration.Inf)
    }

    val projectFields = List(
      field("organization").typed(StringType).index("not_analyzed"),
      field("repository").typed(StringType).index("not_analyzed"),
      field("keywords").typed(StringType).index("not_analyzed"),
      field("defaultArtifact").typed(StringType).index("no"),
      field("artifacts").typed(StringType).index("not_analyzed"),
      field("customScalaDoc").typed(StringType).index("no"),
      field("artifactDeprecations").typed(StringType).index("no"),
      field("cliArtifacts").typed(StringType).index("no"),
      field("created").typed(DateType),
      field("updated").typed(DateType),
      field("targets").typed(StringType).index("not_analyzed"),
      field("dependencies").typed(StringType).index("not_analyzed")
    )

    val releasesFields = List(
      field("reference")
        .nested(
          field("organization").typed(StringType).index("not_analyzed"),
          field("repository").typed(StringType).index("not_analyzed"),
          field("artifact").typed(StringType).index("not_analyzed")
        )
        .includeInRoot(true),
      field("maven").nested(
        field("groupId").typed(StringType).index("not_analyzed"),
        field("artifactId").typed(StringType).index("not_analyzed"),
        field("version").typed(StringType).index("not_analyzed")
      ),
      field("released").typed(DateType),
      field("version").typed(StringType).index("not_analyzed"),
      field("targetType").typed(StringType).index("not_analyzed"),
      field("scalaVersion").typed(StringType).index("not_analyzed"),
      field("scalaJsVersion").typed(StringType).index("not_analyzed"),
      field("scalaNativeVersion").typed(StringType).index("not_analyzed")
    )

    println("creating index")
    Await.result(esClient.execute {
      create
        .index(indexName)
        .mappings(
          mapping(projectsCollection).fields(projectFields: _*),
          mapping(releasesCollection).fields(releasesFields: _*)
        )
    }, Duration.Inf)

    println("loading update data")
    val projectConverter = new ProjectConvert(paths)
    val newData = projectConverter(
      PomsReader.loadAll(paths).collect { case Success(pomAndMeta) => pomAndMeta }
    )

    val (projects, projectReleases) = newData.unzip
    val releases = projectReleases.flatten

    val progress = new ProgressBar("Indexing releases", releases.size)
    progress.start()
    val bunch = 1000
    releases.grouped(bunch).foreach { group =>
      Await.result(esClient.execute {
        bulk(group.map(release => index.into(indexName / releasesCollection).source(release)))
      }, Duration.Inf)
      progress.stepBy(bunch)
    }

    val bunch2 = 100
    println(s"Indexing projects (${projects.size})")
    projects.grouped(bunch2).foreach { group =>
      Await.result(esClient.execute {
        bulk(group.map(project => index.into(indexName / projectsCollection).source(project)))
      }, Duration.Inf)
    }

    ()
  }
}
