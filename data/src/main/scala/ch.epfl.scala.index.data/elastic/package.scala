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

  implicit val formats = Serialization.formats(
      ShortTypeHints(
          List(
              classOf[Milestone],
              classOf[ReleaseCandidate],
              classOf[OtherPreRelease],
              classOf[BintrayResolver]
          )))

  implicit val serialization = native.Serialization

  implicit object ProjectAs extends HitAs[Project] {

    override def as(hit: RichSearchHit): Project =
      read[Project](hit.sourceAsString).copy(_id = Some(hit.getId))
  }

  implicit object ProjectIndexable extends Indexable[Project] {
    override def json(project: Project): String = write(project)
  }

  implicit object ReleaseAs extends HitAs[Release] {
    override def as(hit: RichSearchHit): Release =
      read[Release](hit.sourceAsString).copy(_id = Some(hit.getId))
  }

  implicit object ReleaseIndexable extends Indexable[Release] {
    override def json(release: Release): String = write(release)
  }
}

package object elastic extends ProjectProtocol {

  /** @see https://github.com/sksamuel/elastic4s#client for configurations */
  val maxResultWindow = 10000 // <=> max amount of projects (June 1st 2016 ~ 2500 projects)
  private val base    = build.info.BuildInfo.baseDirectory.toPath
  val esSettings = Settings
    .settingsBuilder()
    .put("path.home", base.resolve(".esdata").toString())
    .put("max_result_window", maxResultWindow)

  lazy val esClient = ElasticClient.local(esSettings.build)

  val indexName          = "scaladex"
  val projectsCollection = "projects"
  val releasesCollection = "releases"

  private def blockUntil(explain: String)(predicate: () => Boolean): Unit = {

    var backoff = 0
    var done    = false
    while (backoff <= 128 && !done) {
      if (backoff > 0) Thread.sleep(200L * backoff)
      backoff = backoff + 1
      done = predicate()
    }
    require(done, s"Failed waiting on: $explain")
  }

  def blockUntilYellow(): Unit = {

    import ElasticDsl._

    blockUntil("Expected cluster to have yellow status") { () =>
      esClient.execute {
        get cluster health
      }.await.getStatus == ClusterHealthStatus.YELLOW
    }
  }

  def blockUntilCount(expected: Int): Unit = {
    blockUntil(s"Expected count of $expected") { () =>
      expected <= {
        val res = esClient.execute {
          search.in(indexName / releasesCollection).query(matchAllQuery).size(0)
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
