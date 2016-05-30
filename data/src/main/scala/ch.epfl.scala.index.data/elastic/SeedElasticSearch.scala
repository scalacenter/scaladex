package ch.epfl.scala.index
package data
package elastic

import project._
import maven.PomsReader

import com.sksamuel.elastic4s._
import ElasticDsl._
import mappings.FieldType._

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Success

class SeedElasticSearch extends ProjectProtocol {
  def run(): Unit = {

    val projects = 
      ProjectConvert(
        PomsReader
          .load()
          .collect {case Success(pomAndMeta) => pomAndMeta}
      )

    println("mapping ES")
    Await.result(
      esClient.execute {
        create.index(indexName).mappings(
          mapping(collectionName) fields (
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
          )
        )
      },
      Duration.Inf
    )

    println("indexing to ES")
    Await.result(
      esClient.execute {
        bulk(
          projects.map(project => 
            index into indexName / collectionName source project
          )
        )
      },
      Duration.Inf
    )

    ()
  }
}