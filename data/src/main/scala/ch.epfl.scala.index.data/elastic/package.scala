package ch.epfl.scala.index
package data

import model._
import release._

import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.ElasticDsl.{get, _}
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.cluster.health.ClusterHealthStatus
import source.Indexable

import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}

import com.typesafe.config.ConfigFactory

// import java.nio.file.Paths

trait ProjectProtocol {

  implicit val formats = Serialization.formats(
    ShortTypeHints(
      List(
        classOf[Milestone],
        classOf[ReleaseCandidate],
        classOf[OtherPreRelease],
        classOf[BintrayResolver]
      ))) + artifactKindSerializer

  implicit val serialization = native.Serialization

  implicit object ProjectAs extends HitAs[Project] {

    override def as(hit: RichSearchHit): Project =
      read[Project](hit.sourceAsString).copy(id = Some(hit.getId))
  }

  implicit object ProjectIndexable extends Indexable[Project] {
    override def json(project: Project): String = write(project)
  }

  implicit object ReleaseAs extends HitAs[Release] {
    override def as(hit: RichSearchHit): Release =
      read[Release](hit.sourceAsString).copy(id = Some(hit.getId))
  }

  implicit object ReleaseIndexable extends Indexable[Release] {
    override def json(release: Release): String = write(release)
  }

  lazy val artifactKindSerializer: Serializer[ArtifactKind] =
    new CustomSerializer[ArtifactKind](formats => {
      (
        {
          case JString("sbt-plugin")     => ArtifactKind.SbtPlugin
          case JString("conventional")   => ArtifactKind.ConventionalScalaLib
          case JString("unconventional") => ArtifactKind.UnconventionalScalaLib
        },
        {
          case ArtifactKind.SbtPlugin              => JString("sbt-plugin")
          case ArtifactKind.ConventionalScalaLib   => JString("conventional")
          case ArtifactKind.UnconventionalScalaLib => JString("unconventional")
        }
      )
    })
}

package object elastic extends ProjectProtocol {

  /** @see https://github.com/sksamuel/elastic4s#client for configurations */
  lazy val esClient = {
    val config = ConfigFactory.load().getConfig("org.scala_lang.index.data")
    val elasticsearch = config.getString("elasticsearch")

    println(s"elasticsearch $elasticsearch")
    if (elasticsearch == "remote") {
      ElasticClient.transport(ElasticsearchClientUri("localhost", 9300))
    } else if (elasticsearch == "local") {
      val base = build.info.BuildInfo.baseDirectory.toPath
      println(base.resolve(".esdata").toString())
      val esSettings =
        Settings.settingsBuilder().put("path.home", base.resolve(".esdata").toString())
      ElasticClient.local(esSettings.build)
    } else {
      sys.error(
        s"org.scala_lang.index.data.elasticsearch should be remote or local: $elasticsearch")
    }
  }

  val indexName = "scaladex"
  val projectsCollection = "projects"
  val releasesCollection = "releases"

  def blockUntilYellow(): Unit = {
    def blockUntil(explain: String)(predicate: () => Boolean): Unit = {
      var backoff = 0
      var done = false
      while (backoff <= 128 && !done) {
        if (backoff > 0) Thread.sleep(200L * backoff)
        backoff = backoff + 1
        done = predicate()
      }
      require(done, s"Failed waiting on: $explain")
    }

    import ElasticDsl._

    blockUntil("Expected cluster to have yellow status") { () =>
      val status =
        esClient.execute {
          get cluster health
        }.await.getStatus

      status == ClusterHealthStatus.YELLOW ||
      status == ClusterHealthStatus.GREEN
    }
  }
}
