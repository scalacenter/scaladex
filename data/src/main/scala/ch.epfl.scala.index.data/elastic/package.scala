package ch.epfl.scala.index
package data

import model._
import release._

import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.embedded.LocalNode
import com.sksamuel.elastic4s.ElasticDsl._
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.cluster.health.ClusterHealthStatus

import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read => nread, write => nwrite}

import com.typesafe.config.ConfigFactory

import scala.util.{Try, Success}

trait ProjectProtocol {

  implicit val formats = Serialization.formats(
    ShortTypeHints(
      List(
        classOf[Milestone],
        classOf[ReleaseCandidate],
        classOf[OtherPreRelease],
        classOf[BintrayResolver]
      ))) + artifactKindSerializer

  // implicit val serialization = native.Serialization

  private def tryEither[T](f: T): Either[Throwable, T] = {
    Try(f).transform(s => Success(Right(s)),f => Success(Left(f))).get
  }

  implicit object ProjectAs extends HitReader[Project] {

    override def read(hit: Hit): Either[Throwable, Project] =
      tryEither(nread[Project](hit.sourceAsString).copy(id = Some(hit.id)))
  }

  implicit object ProjectIndexable extends Indexable[Project] {
    override def json(project: Project): String = nwrite(project)
  }

  implicit object ReleaseAs extends HitReader[Release] {

    override def read(hit: Hit): Either[Throwable, Release] =
      tryEither(nread[Release](hit.sourceAsString).copy(id = Some(hit.id)))
  }

  implicit object ReleaseIndexable extends Indexable[Release] {
    override def json(release: Release): String = nwrite(release)
  }

  lazy val artifactKindSerializer: Serializer[ArtifactKind] =
    new CustomSerializer[ArtifactKind](formats => {
      (
        {
          case JString("sbt-plugin")     => ArtifactKind.SbtPlugin
          case JString("conventional")   => ArtifactKind.ConventionalScalaLib
          case JString("unconventional") => ArtifactKind.UnconventionalScalaLib
        }, {
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
      TcpClient.transport(ElasticsearchClientUri("localhost", 9300))
    } else if (elasticsearch == "local") {
      val base = build.info.BuildInfo.baseDirectory.toPath
      println(base.resolve(".esdata").toString())
      val esSettings =
        Settings
          .builder()
          .put("path.home", base.resolve(".esdata").toString())
          .build()

      LocalNode(esSettings).elastic4sclient()
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

    // import ElasticDsl._

    blockUntil("Expected cluster to have yellow status") { () =>
      val status =
        esClient
          .execute {
            clusterHealth()
          }
          .await
          .getStatus

      status == ClusterHealthStatus.YELLOW ||
      status == ClusterHealthStatus.GREEN
    }
  }
}
