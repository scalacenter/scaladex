package ch.epfl.scala.index
package server

import api._
import model._

import data.elastic._
import com.sksamuel.elastic4s._
import ElasticDsl._
import org.elasticsearch.search.sort.SortOrder

import upickle.default.{Reader, Writer, write => uwrite, read => uread}

import scala.concurrent.{Future, ExecutionContext}
import scala.language.reflectiveCalls

object AutowireServer extends autowire.Server[String, Reader, Writer]{
  def read[Result: Reader](p: String)  = uread[Result](p)
  def write[Result: Writer](r: Result) = uwrite(r)
}

object ApiImplementation {
  def setup = esClient.execute { indexExists(indexName) }
}

class ApiImplementation(github: Github, userState: Option[UserState])(implicit val ec: ExecutionContext) extends Api {
  private def hideId(p: Project) = p.copy(_id = None)

  def userInfo(): Option[UserInfo] = userState.map(_.user)
  def autocomplete(q: String): Future[List[(String, String, String)]] = {
    find(q, 0).map{ case (_, projects) =>
      (for {
        project <- projects
        artifact <- project.artifacts
      } yield (
        artifact.reference.organization,
        artifact.reference.name,
        project.github.flatMap(_.description).getOrElse("")
      )).take(10)
    }
  }
  def find(queryString: String, page: PageIndex, sorting: Option[String] = None): Future[(Pagination, List[Project])] = {
    val perPage = 10
    val clampedPage = if(page <= 0) 1 else page

    val sortQuery =
      sorting match {
        case Some("stars") => fieldSort("github.stars") missing "0" order SortOrder.DESC mode MultiMode.Avg
        case Some("forks") => fieldSort("github.forks") missing "0" order SortOrder.DESC mode MultiMode.Avg
        case Some("relevant") => scoreSort
        case _ => scoreSort
      }

    esClient.execute {
      search
        .in(indexName / collectionName)
        .query(queryString)
        .start(perPage * (clampedPage - 1))
        .limit(perPage)
        .sort(sortQuery)
    }.map(r => (
      Pagination(
        current = clampedPage,
        totalPages = Math.ceil(r.totalHits / perPage.toDouble).toInt,
        total = r.totalHits
      ),
      r.as[Project].toList.map(hideId)
    ))
  }

  def projectPage(artifact: Artifact.Reference): Future[Option[Project]] = {
    val Artifact.Reference(organization, artifactName) = artifact
    esClient.execute {
      search.in(indexName / collectionName).query(
        nestedQuery("artifacts.reference").query(
          bool (
            must(
              termQuery("artifacts.reference.organization", organization),
              termQuery("artifacts.reference.name", artifactName)
            )
          )
        )
      ).limit(1)
    }.map(r => r.as[Project].headOption.map(hideId))
  }

  def latest(artifact: Artifact.Reference): Future[Option[Release.Reference]] = {
    projectPage(artifact).map(_.flatMap(
      _.artifacts
        .find(_.reference == artifact)
        .flatMap(_.releases.headOption.map(_.reference))
    ))
  }

  def keywords(): Future[Map[String, Long]] = {
    import scala.collection.JavaConverters._
    import org.elasticsearch.search.aggregations.bucket.terms.StringTerms
    val aggregationName = "keywords_count"
    esClient.execute {
      search.in(indexName / collectionName).aggregations(
        aggregation.terms(aggregationName).field("keywords").size(50)
      )
    }.map( resp => {
      val agg = resp.aggregations.get[StringTerms](aggregationName)
      agg.getBuckets.asScala.toList.collect{
        case b: StringTerms.Bucket => b.getKeyAsString -> b.getDocCount
      }.toMap
    })
  }


}