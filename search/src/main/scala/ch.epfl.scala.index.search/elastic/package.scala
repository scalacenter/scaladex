package ch.epfl.scala.index.search

import java.io.File
import com.typesafe.scalalogging.LazyLogging
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.model.misc.GithubIssue
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
          classOf[BintrayResolver],
          classOf[ScalaJvm],
          classOf[ScalaJs],
          classOf[ScalaNative],
          classOf[SbtPlugin],
          classOf[MajorBinary],
          classOf[MinorBinary],
          classOf[PatchBinary],
          classOf[PreReleaseBinary],
          classOf[ScalaVersion],
          classOf[DottyVersion]
        )
      )
    )
    .preservingEmptyValues

  private def tryEither[T](f: T): Either[Throwable, T] = {
    Try(f).transform(s => Success(Right(s)), f => Success(Left(f))).get
  }

  private def nread[T: Manifest](hit: Hit) =
    Parser.parseUnsafe(hit.sourceAsString).extract[T]

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
      tryEither(
        checkInnerHits(hit.asInstanceOf[RichSearchHit],
                       nread[Project](hit).copy(id = Some(hit.id)))
      )
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

package object elastic extends ProjectProtocol with LazyLogging {

  private val config = ConfigFactory.load().getConfig("org.scala_lang.index.data")
  private val elasticsearch = config.getString("elasticsearch")

  /** @see https://github.com/sksamuel/elastic4s#client for configurations */
  def esClient(baseDirectory: File): TcpClient = {
    logger.info(s"elasticsearch $elasticsearch $indexName")
    if (elasticsearch == "remote") {
      TcpClient.transport(ElasticsearchClientUri("localhost", 9300))
    } else if (elasticsearch == "local" || elasticsearch == "local-prod") {
      val homePath =
        if (elasticsearch == "local-prod") {
          "."
        } else {
          val base = baseDirectory.toPath
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
      logger.error(er)
      sys.error(er)
    }
  }

  val indexName: String = config.getString("index")

  val projectsCollection = "projects"
  val releasesCollection = "releases" 
}
