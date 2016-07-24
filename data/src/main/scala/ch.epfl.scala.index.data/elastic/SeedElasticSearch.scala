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

class SeedElasticSearch(implicit val ec: ExecutionContext) extends ProjectProtocol {
  def run(): Unit = {

    val exists = Await.result(esClient.execute { indexExists(indexName) }, Duration.Inf).isExists()

    if (!exists) {
      println("creating index")
      Await.result(esClient.execute {
        create
          .index(indexName)
          .mappings(
            mapping(projectsCollection).fields(
              field("organization").typed(StringType).index("not_analyzed"),
              field("repository").typed(StringType).index("not_analyzed"),
              field("keywords").typed(StringType).index("not_analyzed"),
              field("created").typed(DateType),
              field("updated").typed(DateType),
              field("targets").typed(StringType).index("not_analyzed"),
              field("dependencies").typed(StringType).index("not_analyzed"),

              field("defaultArtifact").typed(StringType).index("no"),
              field("customScalaDoc").typed(StringType).index("no"),
              field("documentationLinks").typed(StringType).index("no"),
              field("artifactDeprecations").typed(StringType).index("no"),
              field("logoImageUrl").typed(StringType).index("no"),
              field("background").typed(StringType).index("no"),
              field("foregroundColor").typed(StringType).index("no")
            ),
            mapping(releasesCollection).fields(
              field("reference").nested(
                  field("organization").typed(StringType).index("not_analyzed"),
                  field("repository").typed(StringType).index("not_analyzed"),
                  field("artifact").typed(StringType).index("not_analyzed")
              ).includeInRoot(true),
              field("maven").nested(
                field("groupId").typed(StringType).index("not_analyzed"),
                field("artifactId").typed(StringType).index("not_analyzed"),
                field("version").typed(StringType).index("not_analyzed")
              ),
              field("released").typed(DateType)
            )
          )
      }, Duration.Inf)
    }

    println("loading update data")
    val newData = ProjectConvert(
        PomsReader.load().collect { case Success(pomAndMeta) => pomAndMeta }
    )

    val (projects, projectReleases) = newData.unzip
    val releases                    = projectReleases.flatten

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
