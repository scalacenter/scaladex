package ch.epfl.scala.index
package data
package elastic

import model.Project
import project._
import maven.PomsReader

import com.sksamuel.elastic4s._
import ElasticDsl._
import mappings.FieldType._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

class SeedElasticSearch(implicit val ec: ExecutionContext) extends ProjectProtocol {
  def run(): Unit = {

    val exists = Await.result(esClient.execute { indexExists(indexName)}, Duration.Inf).isExists()

    val target = indexName / collectionName

    if(!exists) {
      println("creating index")
      Await.result(esClient.execute {
        create.index(indexName).mappings(mapping(collectionName).fields(
          field("parentOrganization") typed StringType index "not_analyzed",
          field("reference").nested(
            field("organization") typed StringType index "not_analyzed",
            field("repository") typed StringType index "not_analyzed"
          ),
          field("artifacts").nested(
            field("reference").nested(
              field("organization") typed StringType index "not_analyzed",
              field("name") typed StringType index "not_analyzed"
            ),
            field("releases").nested(
              field("reference").nested(
                field("organization") typed StringType index "not_analyzed",
                field("artifact") typed StringType index "not_analyzed"
              )
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
    Thread.sleep(500)

    // XXX: at this point we would like to lock elasticsearch for updating
    // https://github.com/sksamuel/elastic4s/issues/578
    val projectsLive = 
      if(exists){
        println("importing live data")
      
        Await.result(esClient.execute { 
          search
          .in(indexName / collectionName)
          .query(matchAllQuery)
          .limit(maxResultWindow)
        }.map(_.as[Project].toList), Duration.Inf)
      } else {
        println("index is empty")
        List()
      }

    println("calculating project deltas")
    val deltas = ProjectDelta(live = projectsLive, update = projectsUpdate)

    println(deltas.collect{ case NewProject(_) => 1}.size)
    println(deltas.collect{ case UpdatedProject(_) => 1}.size)

    val elasticDeltas = deltas.map{
      case NewProject(project)     => Some(index.into(target).source(project))
      case UpdatedProject(project) => project._id.map(id => update.id(id).in(target).doc(project))
      case NoOp                    => None
    }.flatten

    println("indexing deltas to ES")
    if(!elasticDeltas.isEmpty) {
      Await.result( esClient.execute {bulk(elasticDeltas)}, Duration.Inf)
    }

    ()
  }
}