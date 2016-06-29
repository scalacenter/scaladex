package ch.epfl.scala.index
package server

import model._
import misc.{GithubRepo, Pagination, UserInfo}

import data.elastic._
import com.sksamuel.elastic4s._
import ElasticDsl._
import org.elasticsearch.search.sort.SortOrder

import scala.concurrent.{ExecutionContext, Future}
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

  /**
   * get Keyword list
   * @return
   */
  def keywords() = aggregations("keywords")

  
  def targets(): Future[Map[String, Long]] = aggregations("targets")

  def dependencies(): Future[List[(String, Long)]] = {
    val testOrLogin = Set(
      "scalatest/scalatest",
      "scoverage/scalac-scoverage-plugin",
      "rickynils/scalacheck",
      "scoverage/scalac-scoverage-runtime",
      "etorreborre/specs2",
      "etorreborre/specs2-core",
      "akka/akka-testkit",
      "playframework/play-test",
      "typesafehub/scala-logging",
      "paulbutcher/scalamock-scalatest-support",
      "typesafehub/scala-logging-slf4j",
      "scopt/scopt",
      "etorreborre/specs2-junit",
      "etorreborre/specs2-mock",
      "akka/akka-slf4j",
      "etorreborre/specs2-scalacheck",
      "scalaz/scalaz-scalacheck-binding",
      "spray/spray-testkit",
      "playframework/play-specs2"
    )

    aggregations("dependencies").map(agg =>
      agg.toList.sortBy(_._2)(Descending).filter{ case (ref, _) =>
        !testOrLogin.contains(ref)
      }
    )
  }

  /**
   * list all tags including number of facets
   * @param field the field name
   * @return
   */
  private def aggregations(field: String): Future[Map[String, Long]] = {

    import scala.collection.JavaConverters._
    import scala.collection.immutable.ListMap
    import org.elasticsearch.search.aggregations.bucket.terms.StringTerms

    val aggregationName = s"${field}_count"

    esClient.execute {
      search.in(indexName / collectionName).aggregations(
        aggregation.terms(aggregationName).field(field).size(50)
      )
    }.map( resp => {

      val agg = resp.aggregations.get[StringTerms](aggregationName)
      val aggs = agg.getBuckets.asScala.toList.collect {
        case b: StringTerms.Bucket => b.getKeyAsString -> b.getDocCount
      }
      ListMap(aggs.sortBy(_._1): _*)
    })
  }
}