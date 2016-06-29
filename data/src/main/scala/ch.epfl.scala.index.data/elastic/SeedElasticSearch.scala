package ch.epfl.scala.index
package data
package elastic

import model.Project
import project._

import maven.PomsReader

import com.sksamuel.elastic4s._
import ElasticDsl._
import mappings.FieldType._
import org.elasticsearch.cluster.health.ClusterHealthStatus

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

class SeedElasticSearch(implicit val ec: ExecutionContext) extends ProjectProtocol {
  def run(): Unit = {

    val exists = Await.result(esClient.execute { indexExists(indexName)}, Duration.Inf).isExists()
    val target = indexName / collectionName

    def blockUntilCount(expected: Int): Unit = {
      blockUntil(s"Expected count of $expected") {
        () =>
          expected <= {
            val res = esClient.execute {
              search
                .in(indexName / collectionName)
                .query(matchAllQuery)
                .size(0)
            }.await.totalHits
            println(res)
            res
          }

      }
    }
    def blockUntilGreen(): Unit = {
      blockUntil("Expected cluster to have green status") { () =>
        esClient.execute {
          get cluster health
        }.await.getStatus == ClusterHealthStatus.GREEN
      }
    }
    def blockUntil(explain: String)(predicate: () => Boolean): Unit = {
      var backoff = 0
      var done = false
      while (backoff <= 16 && !done) {
        if (backoff > 0) Thread.sleep(200L * backoff)
        backoff = backoff + 1
        done = predicate()
      }
      require(done, s"Failed waiting on: $explain")
    }


    if (!exists) {

      println("creating index")
      Await.result(esClient.execute {
        create.index(indexName).mappings(mapping(collectionName).fields(
          field("keywords") typed StringType index "not_analyzed",
          field("targets") typed StringType index "not_analyzed",
          field("dependencies") typed StringType index "not_analyzed",
          field("parentOrganization") typed StringType index "not_analyzed",
          field("firstUpdate").typed(DateType),
          field("lastUpdate").typed(DateType),
          field("reference").nested(
            field("organization") typed StringType index "not_analyzed",
            field("repository") typed StringType index "not_analyzed"
          ),
          field("artifacts").nested(
            field("reference").nested(
              field("organization") typed StringType index "not_analyzed",
              field("name") typed StringType index "not_analyzed"
            )
          )
        ))
      }, Duration.Inf)
    }

    println("loading update data")
    val projectsUpdate = 
      ProjectConvert(
        PomsReader
          .load()
          .collect {case Success(pomAndMeta) => pomAndMeta}
      )

    // NB: if you get "at org.elasticsearch.action.search.AbstractSearchAsyncAction.onFirstPhaseResult"
    //     elasticsearch is just not yet ready for querying
    // see also: https://github.com/elastic/elasticsearch/issues/1063
    // blockUntilGreen()
    Thread.sleep(500)

    // XXX: at this point we would like to lock elasticsearch for updating
    // https://github.com/sksamuel/elastic4s/issues/578
    val projectsLive = 
      if (exists) {

        println("importing live data")
      
        Await.result(esClient.execute { 
          search
          .in(indexName / collectionName)
          .query(matchAllQuery)
          .limit(maxResultWindow) // TODO will be a problem if there are more than 10.000 projects
        }.map(_.as[Project].toList), Duration.Inf)
      } else {

        println("index is empty")
        List()
      }

    println(s"calculating project deltas of ${projectsUpdate.size} Projects")
    val deltas = ProjectDelta(live = projectsLive, update = projectsUpdate)

    val inserts = deltas.collect{ case NewProject(project) => index.into(target).source(project) }
    val updates = deltas.collect{ 
      case UpdatedProject(project) => project._id.map(id => update.id(id).in(target).doc(project))
    }.flatten

    println("indexing deltas to ES")
    println(s"index ${inserts.size} new documents")

    if (inserts.nonEmpty) {

      Await.result( esClient.execute {bulk(inserts)}, Duration.Inf)
    }

    println(s"update ${updates.size} documents")
    if (updates.nonEmpty) {

      Await.result( esClient.execute {bulk(updates)}, Duration.Inf)
    }

    // blockUntilCount(inserts.size)

    ()
  }
}