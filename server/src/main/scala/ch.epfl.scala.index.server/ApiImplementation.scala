package ch.epfl.scala.index
package server

import model._

import data.elastic._
import com.sksamuel.elastic4s._
import ElasticDsl._
import org.elasticsearch.search.sort.SortOrder

import scala.concurrent.{Future, ExecutionContext}
import scala.language.reflectiveCalls

class ApiImplementation(github: Github, userState: Option[UserState])(implicit val ec: ExecutionContext) {
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
  def find(queryString: String, page: PageIndex, sorting: Option[String] = None, repos: Option[Set[GithubRepo]] = None): Future[(Pagination, List[Project])] = {
    val perPage = 10
    val clampedPage = if(page <= 0) 1 else page

    val sortQuery =
      sorting match {
        case Some("stars") => fieldSort("github.stars") missing "0" order SortOrder.DESC mode MultiMode.Avg
        case Some("forks") => fieldSort("github.forks") missing "0" order SortOrder.DESC mode MultiMode.Avg
        case Some("relevant") => scoreSort
        case Some("created") => fieldSort("created") order SortOrder.DESC
        case Some("updated") => fieldSort("lastUpdate") order SortOrder.DESC
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

  def latestProjects(): Future[List[Project]] = latest("created", 12)
  def latestReleases(): Future[List[Release]] = {
    import com.github.nscala_time.time.Imports._
    import org.joda.time.format.ISODateTimeFormat
    val format = ISODateTimeFormat.dateTime.withOffsetParsed

    latest("updated", 12).map(projects =>
      (for {
        project  <- projects
        artifact <- project.artifacts
        release  <- artifact.releases
      } yield release).sortBy(release => 
        maxOption(release.releaseDates.map(
          date => format.parseDateTime(date.value)
        ))
      )(Descending)
    )
  }
  private def maxOption[T: Ordering](xs: List[T]): Option[T] = if(xs.isEmpty) None else Some(xs.max)

  def latest(by: String, n: Int): Future[List[Project]] = {
    esClient.execute {
      search.in(indexName / collectionName)
        .query(matchAllQuery)
        .sort(fieldSort("created") order SortOrder.DESC)
        .limit(n)
    }.map(r => r.as[Project].toList.map(hideId)) 
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