package ch.epfl.scala.index
package data

import com.sksamuel.elastic4s.ElasticDsl.{get, _}
import model._
import release._
import org.elasticsearch.common.settings.Settings
import com.sksamuel.elastic4s._
import org.elasticsearch.cluster.health.ClusterHealthStatus
import source.Indexable
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}

trait ProjectProtocol {

  implicit val formats = Serialization.formats(ShortTypeHints(List(
    classOf[Milestone],
    classOf[ReleaseCandidate],
    classOf[OtherPreRelease]
  )))

  implicit val serialization = native.Serialization

  implicit object ProjectAs extends HitAs[Project] {

    override def as(hit: RichSearchHit): Project = read[Project](hit.sourceAsString)
  }

  implicit object ProjectIndexable extends Indexable[Project] {

    override def json(project: Project): String = write(project)
  }
}

package object elastic extends ProjectProtocol {

  /** @see https://github.com/sksamuel/elastic4s#client for configurations */

  val maxResultWindow = 10000 // <=> max amount of projects (June 1st 2016 ~ 2500 projects)
  private val home = System.getProperty("user.home")
  val esSettings = Settings.settingsBuilder()
    .put("path.home", home + "/.esdata")
    .put("max_result_window", maxResultWindow)

  lazy val esClient = ElasticClient.local(esSettings.build)

  val indexName = "scaladex"
  val collectionName = "artifacts"

  private def blockUntil(explain: String)(predicate: () => Boolean): Unit = {

    var backoff = 0
    var done = false
    while (backoff <= 16 && !done) {
      if (backoff > 0) Thread.sleep(200L * backoff)
      backoff = backoff + 1
      done = predicate()
    }
    require(done, s"Failed waiting on: $explain")
  }

  def blockUntilYellow(): Unit = {

    import ElasticDsl._

    blockUntil("Expected cluster to have green status") { () =>
      esClient.execute {
        get cluster health
      }.await.getStatus == ClusterHealthStatus.YELLOW
    }
  }

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
}