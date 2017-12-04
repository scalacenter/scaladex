package ch.epfl.scala.index
package data

import model._
import model.misc.GithubIssue
import release._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.embedded.LocalNode
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.searches.RichSearchHit
import org.elasticsearch.cluster.health.ClusterHealthStatus
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{write => nwrite}
import com.typesafe.config.ConfigFactory
import jawn.support.json4s.Parser
import org.slf4j.LoggerFactory

import scala.util.{Success, Try}

trait ProjectProtocol {

  implicit val formats = Serialization
    .formats(
      ShortTypeHints(
        List(
          classOf[Milestone],
          classOf[ReleaseCandidate],
          classOf[OtherPreRelease],
          classOf[BintrayResolver]
        )
      )
    )
    .preservingEmptyValues

  private def tryEither[T](f: T): Either[Throwable, T] = {
    Try(f).transform(s => Success(Right(s)), f => Success(Left(f))).get
  }

  private def nread[T: Manifest](hit: Hit) = Parser.parseUnsafe(hit.sourceAsString).extract[T]

  // filters a project's beginnerIssues by the inner hits returned from elastic search
  // so that only the beginnerIssues that passed the nested beginnerIssues query
  // get returned
  private def checkInnerHits(hit: RichSearchHit, p: Project): Project = {
    hit.innerHits
      .get("issues")
      .collect {
        case searchHits if searchHits.totalHits > 0 => {
          p.copy(
            github = p.github.map { github =>
              github.copy(
                filteredBeginnerIssues = searchHits
                  .getHits()
                  .map { hit =>
                    nread[GithubIssue](RichSearchHit(hit))
                  }
                  .toList
              )
            }
          )
        }
      }
      .getOrElse(p)
  }

  implicit object ProjectAs extends HitReader[Project] {

    override def read(hit: Hit): Either[Throwable, Project] = {
      tryEither(checkInnerHits(hit.asInstanceOf[RichSearchHit], nread[Project](hit).copy(id = Some(hit.id))))
    }
  }

  implicit object ProjectIndexable extends Indexable[Project] {
    override def json(project: Project): String = nwrite(project)
  }

  implicit object ReleaseAs extends HitReader[Release] {

    override def read(hit: Hit): Either[Throwable, Release] =
      tryEither(nread[Release](hit).copy(id = Some(hit.id)))
  }

  implicit object ReleaseIndexable extends Indexable[Release] {
    override def json(release: Release): String = nwrite(release)
  }
}

package object elastic extends ProjectProtocol {

  private val log = LoggerFactory.getLogger(getClass)

  private val config =
    ConfigFactory.load().getConfig("org.scala_lang.index.data")
  private val elasticsearch = config.getString("elasticsearch")

  /** @see https://github.com/sksamuel/elastic4s#client for configurations */
  lazy val esClient = {

    log.info(s"elasticsearch $elasticsearch")
    if (elasticsearch == "remote") {
      TcpClient.transport(ElasticsearchClientUri("localhost", 9300))
    } else if (elasticsearch == "local" || elasticsearch == "local-prod") {
      val homePath =
        if (elasticsearch == "local-prod") {
          "."
        } else {
          val base = build.info.BuildInfo.baseDirectory.toPath
          base.resolve(".esdata").toString()
        }

      LocalNode(
        LocalNode.requiredSettings(
          clusterName = "elasticsearch-local",
          homePath = homePath
        )
      ).elastic4sclient()
    } else {
      val er =
        s"org.scala_lang.index.data.elasticsearch should be remote or local: $elasticsearch"
      log.error(er)
      sys.error(er)
    }
  }

  private val production = config.getBoolean("production")

  val indexName =
    if (production) "scaladex"
    else "scaladex-dev"

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
