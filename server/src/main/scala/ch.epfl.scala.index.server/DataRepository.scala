package ch.epfl.scala.index
package server

import model._
import model.misc._
import data.project.ProjectForm
import release._
import misc.Pagination
import data.elastic._
import com.sksamuel.elastic4s._
import ElasticDsl._
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier
import org.elasticsearch.search.sort.SortOrder

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

class DataRepository(github: Github)(private implicit val ec: ExecutionContext) {
  private def hideId(p: Project) = p.copy(id = None)

  val sortQuery = (sorting: Option[String]) =>
    sorting match {
      case Some("stars") =>
        fieldSort("github.stars") missing "0" order SortOrder.DESC mode MultiMode.Avg
      case Some("forks") =>
        fieldSort("github.forks") missing "0" order SortOrder.DESC mode MultiMode.Avg
      case Some("dependentCount") =>
        fieldSort("dependentCount") missing "0" order SortOrder.DESC mode MultiMode.Avg
      case Some("relevant") => scoreSort
      case Some("created") => fieldSort("created") order SortOrder.DESC
      case Some("updated") => fieldSort("updated") order SortOrder.DESC
      case _ => scoreSort
  }

  private def clamp(page: Int) = if (page <= 0) 1 else page

  private def query(q: QueryDefinition,
                    params: SearchParams): Future[(Pagination, List[Project])] = {

    import params._

    esClient.execute {
      search
        .in(indexName / projectsCollection)
        .query(q)
        .start(params.total * (clamp(page) - 1))
        .limit(params.total)
        .sort(sortQuery(sorting))
    }.map(
      r =>
        (
          Pagination(
            current = clamp(page),
            totalPages = Math.ceil(r.totalHits / params.total.toDouble).toInt,
            total = r.totalHits
          ),
          r.as[Project].toList.map(hideId)
      ))
  }

  private def getQuery(params: SearchParams) = {
    import params._

    def replaceField(queryString: String, input: String, replacement: String) = {
      val regex = s"(\\s|^)$input:".r
      regex.replaceAllIn(queryString, s"$$1$replacement:")
    }

    val translated = replaceField(queryString, "depends-on", "dependencies")

    val escaped =
      if (translated.isEmpty) "*"
      else translated.replaceAllLiterally("/", "\\/")

    val targetsQuery =
      params.targetTypes.map(targetType => bool(should(termQuery("targetType", targetType)))) ++
        params.scalaVersions.map(scalaVersion =>
          bool(should(termQuery("scalaVersion", scalaVersion)))) ++
        params.scalaJsVersions.map(scalaJsVersion =>
          bool(should(termQuery("scalaJsVersion", scalaJsVersion)))) ++
        params.scalaNativeVersions.map(scalaNativeVersion =>
          bool(should(termQuery("scalaNativeVersion", scalaNativeVersion))))

    val cliQuery =
      if (cli) List(termQuery("hasCli", true))
      else Nil

    val keywordsQuery =
      params.keywords.map(keyword => bool(should(termQuery("keywords", keyword))))

    val reposQueries = if (!userRepos.isEmpty) {
      userRepos.toList.map {
        case GithubRepo(organization, repository) =>
          bool(
            must(
              termQuery("organization", organization.toLowerCase),
              termQuery("repository", repository.toLowerCase)
            )
          )
      }
    } else List()

    val mustQueriesRepos =
      if (userRepos.isEmpty) Nil
      else List(bool(should(reposQueries: _*)))

    val stringQ =
      if (escaped.contains(":")) stringQuery(escaped)
      else stringQuery(escaped + "~").fuzzyPrefixLength(3).defaultOperator("AND")

    functionScoreQuery(
      bool(
        mustQueries = mustQueriesRepos ++ cliQuery ++ keywordsQuery ++ targetsQuery,
        shouldQueries = List(
          fuzzyQuery("keywords", escaped).boost(2),
          fuzzyQuery("github.description", escaped).boost(1.7),
          fuzzyQuery("repository", escaped),
          fuzzyQuery("organization", escaped),
          fuzzyQuery("artifacts", escaped),
          stringQ.boost(0.01) // low boost because this query is applied to all the fields of the project (including the github readme)
        ),
        notQueries = List(termQuery("deprecated", true))
      )
    ).scorers(
        // Add a small boost for project that seem to be “popular” (highly depended on or highly starred)
        fieldFactorScore("dependentCount").modifier(Modifier.LOG1P).weight(0.3),
        fieldFactorScore("github.stars").missing(0).modifier(Modifier.LOG1P).weight(0.1)
      )
      .boostMode("sum")
  }

  def total(queryString: String): Future[Long] = {
    esClient.execute {
      search
        .in(indexName / projectsCollection)
        .query(getQuery(SearchParams(queryString = queryString)))
        .size(0)
    }.map(_.totalHits)
  }

  def find(params: SearchParams): Future[(Pagination, List[Project])] =
    query(getQuery(params), params)

  def releases(project: Project.Reference, selection: ReleaseSelection): Future[List[Release]] = {
    esClient.execute {
      search
        .in(indexName / releasesCollection)
        .query(
          nestedQuery("reference").query(
            bool(
              must(
                List(
                  termQuery("reference.organization", project.organization),
                  termQuery("reference.repository", project.repository)
                )
              )
            )
          )
        )
        .size(5000)
    }.map(_.as[Release].toList)
  }

  /**
    * search for a maven artifact
    * @param maven
    * @return
    */
  def maven(maven: MavenReference): Future[Option[Release]] = {

    esClient.execute {
      search
        .in(indexName / releasesCollection)
        .query(
          nestedQuery("maven").query(
            bool(
              must(
                termQuery("maven.groupId", maven.groupId),
                termQuery("maven.artifactId", maven.artifactId),
                termQuery("maven.version", maven.version)
              )
            )
          )
        )
        .limit(1)
    }.map(r => r.as[Release].headOption)
  }

  def project(project: Project.Reference): Future[Option[Project]] = {
    esClient.execute {
      search
        .in(indexName / projectsCollection)
        .query(
          bool(
            must(
              termQuery("organization", project.organization),
              termQuery("repository", project.repository)
            )
          )
        )
        .limit(1)
    }.map(_.as[Project].headOption)
  }

  def projectAndReleases(projectRef: Project.Reference,
                         selection: ReleaseSelection): Future[Option[(Project, List[Release])]] = {
    for {
      project <- project(projectRef)
      releases <- releases(projectRef, selection)
    } yield project.map((_, releases))
  }

  def projectPage(projectRef: Project.Reference,
                  selection: ReleaseSelection): Future[Option[(Project, ReleaseOptions)]] = {
    projectAndReleases(projectRef, selection).map {
      case Some((project, releases)) => {
        DefaultRelease(project.repository,
                       selection,
                       releases.toSet,
                       project.defaultArtifact,
                       project.defaultStableVersion).map(sel => (project, sel))
      }
      case None => None
    }
  }

  def updateProject(projectRef: Project.Reference, form: ProjectForm): Future[Boolean] = {
    for {
      updatedProject <- project(projectRef).map(_.map(p => form.update(p)))
      ret <- updatedProject
        .flatMap(
          project =>
            project.id.map(
              id =>
                esClient
                  .execute(update(id) in (indexName / projectsCollection) doc project)
                  .map(_ => true)))
        .getOrElse(Future.successful(false))
    } yield ret
  }

  private val frontPageCount = 12

  def latestProjects() =
    latest[Project](projectsCollection, "created", frontPageCount).map(_.map(hideId))
  def latestReleases() = latest[Release](releasesCollection, "released", frontPageCount)

  def mostDependedUpon() = {
    esClient.execute {
      search
        .in(indexName / projectsCollection)
        .query(matchAllQuery)
        .limit(frontPageCount)
        .sort(sortQuery(Some("dependentCount")))
    }.map(_.as[Project].toList)
  }

  private def latest[T: HitAs: Manifest](collection: String, by: String, n: Int): Future[List[T]] = {
    esClient.execute {
      search
        .in(indexName / collection)
        .query(
          bool(
            mustQueries = Nil,
            shouldQueries = Nil,
            notQueries = List(termQuery("deprecated", true))
          )
        )
        .sort(fieldSort(by).order(SortOrder.DESC))
        .limit(n)
    }.map(r => r.as[T].toList)
  }

  def keywords(params: SearchParams = SearchParams()): Future[List[(String, Long)]] = {
    stringAggregations("keywords", params).map(addParamsIfMissing(params.keywords))
  }

  def targetTypes(params: SearchParams = SearchParams()): Future[List[(String, Long)]] = {
    stringAggregations("targetType", params).map(addParamsIfMissing(params.targetTypes))
  }

  private def stringAggregations(field: String,
                                 params: SearchParams): Future[List[(String, Long)]] = {
    aggregations(field, params).map(_.toList.sortBy(_._1).toList)
  }

  def scalaVersions(params: SearchParams = SearchParams()): Future[List[(String, Long)]] = {
    val minVer = SemanticVersion(2, 10)
    versionAggregations("scalaVersion", params, _ >= minVer)
      .map(addParamsIfMissing(params.scalaVersions))
  }

  def scalaJsVersions(params: SearchParams = SearchParams()): Future[List[(String, Long)]] = {
    versionAggregations("scalaJsVersion", params, _ => true)
      .map(addParamsIfMissing(params.scalaJsVersions))
  }

  def scalaNativeVersions(params: SearchParams = SearchParams()): Future[List[(String, Long)]] = {
    versionAggregations("scalaNativeVersion", params, _ => true)
      .map(addParamsIfMissing(params.scalaNativeVersions))
  }

  private def addParamsIfMissing(params: List[String])(
      result: List[(String, Long)]): List[(String, Long)] = {
    val pSet = params.toSet
    val rSet = result.map(_._1).toSet

    val diff = pSet -- rSet

    if (diff.nonEmpty) {
      (diff.toList.map(label => (label, 0L)) ++ result).sortBy(_._1)
    } else result
  }

  private def versionAggregations(
      field: String,
      params: SearchParams,
      filterF: SemanticVersion => Boolean): Future[List[(String, Long)]] = {
    def sortedByVersion(aggregation: Map[String, Long]): List[(String, Long)] = {
      aggregation.toList.flatMap {
        case (version, count) => SemanticVersion(version).map(v => (v, count))
      }.filter { case (version, _) => filterF(version) }.groupBy {
        case (version, _) => SemanticVersion(version.major, version.minor)
      }.mapValues(_.map(_._2).sum).toList.sortBy(_._1).map { case (v, c) => (v.toString, c) }
    }
    aggregations(field, params).map(sortedByVersion)
  }

  private def aggregations(field: String, params: SearchParams): Future[Map[String, Long]] = {

    import scala.collection.JavaConverters._
    import org.elasticsearch.search.aggregations.bucket.terms.StringTerms

    val aggregationName = s"${field}_count"

    esClient.execute {
      search
        .in(indexName / projectsCollection)
        .query(getQuery(params))
        .aggregations(aggregation.terms(aggregationName).field(field).size(50))
    }.map(resp => {
      try {
        val agg = resp.aggregations.get[StringTerms](aggregationName)
        agg.getBuckets.asScala.toList.collect {
          case b: StringTerms.Bucket => b.getKeyAsString -> b.getDocCount
        }.toMap
      } catch {
        case e: Exception => {
          println(field)
          e.printStackTrace()
          Map()
        }

      }

    })
  }

  private def maxOption[T: Ordering](xs: List[T]): Option[T] =
    if (xs.isEmpty) None else Some(xs.max)
}
