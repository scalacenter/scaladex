package ch.epfl.scala.index
package server

import model._
import model.misc._

import data.DataPaths
import data.project.ProjectForm
import data.github.{GithubDownload, GithubReader}

import release._
import misc.Pagination
import data.elastic._

import com.sksamuel.elastic4s.HitReader
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.SortDefinition

import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier
import org.elasticsearch.search.sort.SortOrder

import org.slf4j.LoggerFactory

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContext, Future}

/**
  * @param github  Github client
  * @param paths   Paths to the files storing the index
  */
class DataRepository(github: Github, paths: DataPaths)(private implicit val ec: ExecutionContext) {
  private def hideId(p: Project) = p.copy(id = None)

  val logger = LoggerFactory.getLogger(this.getClass)

  val sortQuery: Option[String] => SortDefinition = 
    _ match {
        case Some("stars") =>
          fieldSort("github.stars") missing "0" order SortOrder.DESC // mode MultiMode.Avg
        case Some("forks") =>
          fieldSort("github.forks") missing "0" order SortOrder.DESC // mode MultiMode.Avg
        case Some("dependentCount") =>
          fieldSort("dependentCount") missing "0" order SortOrder.DESC // mode MultiMode.Avg
        case Some("contributors") =>
          fieldSort("github.contributorCount") missing "0" order SortOrder.DESC // mode MultiMode.Avg
        case Some("relevant") => scoreSort order SortOrder.DESC
        case Some("created") => fieldSort("created") order SortOrder.DESC
        case Some("updated") => fieldSort("updated") order SortOrder.DESC
        case _ => scoreSort order SortOrder.DESC
    }

  private def clamp(page: Int) = if (page <= 0) 1 else page

  private def query(q: QueryDefinition,
                    params: SearchParams): Future[(Pagination, List[Project])] = {

    import params._

    esClient.execute {
      search(indexName / projectsCollection)
        .query(q)
        .start(params.total * (clamp(page) - 1))
        .limit(params.total)
        .sortBy(sortQuery(sorting))

    }.map(
      r =>
        (
          Pagination(
            current = clamp(page),
            totalPages = Math.ceil(r.totalHits / params.total.toDouble).toInt,
            total = r.totalHits
          ),
          r.to[Project].toList.map(hideId)
      ))
  }

  private def getQuery(params: SearchParams) = {
    import params._

    def replaceField(queryString: String, input: String, replacement: String) = {
      val regex = s"(\\s|^)$input:".r
      regex.replaceAllIn(queryString, s"$$1$replacement:")
    }

    val translated1 = replaceField(queryString, "depends-on", "dependencies")
    val translated2 = replaceField(translated1, "topics", "github.topics")

    val escaped =
      if (translated2.isEmpty) "*"
      else translated2.replaceAllLiterally("/", "\\/")

    val targetsQuery =
      params.targetTypes.map(targetType => boolQuery().must(termQuery("targetType", targetType))) ++
        params.scalaVersions.map(scalaVersion =>
          boolQuery().must(termQuery("scalaVersion", scalaVersion))) ++
        params.scalaJsVersions.map(scalaJsVersion =>
          boolQuery().must(termQuery("scalaJsVersion", scalaJsVersion))) ++
        params.scalaNativeVersions.map(scalaNativeVersion =>
          boolQuery().must(termQuery("scalaNativeVersion", scalaNativeVersion)))

    val targetQuery = 
      params.targetFiltering.map(target =>

        List(boolQuery().must(termQuery("scalaVersion", target.scalaVersion.toString))) ++

        target.scalaJsVersion.map(jsVersion =>
          List(boolQuery().must(termQuery("scalaJsVersion", target.scalaJsVersion.toString)))
        ).getOrElse(Nil) ++

        target.scalaNativeVersion.map(nativeVersion =>
          List(boolQuery().must(termQuery("scalaNativeVersion", target.scalaNativeVersion.toString)))
        ).getOrElse(Nil)

      ).getOrElse(Nil)

    val cliQuery =
      if (cli) List(termQuery("hasCli", true))
      else Nil

    val topicsQuery =
      params.topics.map(topic => boolQuery().should(termQuery("github.topics", topic)))

    val reposQueries = if (!userRepos.isEmpty) {
      userRepos.toList.map {
        case GithubRepo(organization, repository) =>
          boolQuery().must(
            termQuery("organization", organization.toLowerCase),
            termQuery("repository", repository.toLowerCase)
          )
      }
    } else List()

    val mustQueriesRepos =
      if (userRepos.isEmpty) Nil
      else List(boolQuery().should(reposQueries: _*))

    val stringQ =
      if (escaped.contains(":")) stringQuery(escaped)
      else stringQuery(escaped + "~").fuzzyPrefixLength(3).defaultOperator("AND")

    functionScoreQuery(
      boolQuery().
        must(mustQueriesRepos ++ cliQuery ++ topicsQuery ++ targetsQuery ++ targetQuery).
        should(List(
          fuzzyQuery("github.topics", escaped).boost(2),
          fuzzyQuery("github.description", escaped).boost(1.7),
          fuzzyQuery("repository", escaped),
          fuzzyQuery("organization", escaped),
          fuzzyQuery("artifacts", escaped),
          stringQ.boost(0.01) // low boost because this query is applied to all the fields of the project (including the github readme)
        )).
        not(List(termQuery("deprecated", true)))
    ).scorers(
        // Add a small boost for project that seem to be “popular” (highly depended on or highly starred)
        fieldFactorScore("dependentCount").modifier(Modifier.LOG1P).factor(0.3),
        fieldFactorScore("github.stars").missing(0).modifier(Modifier.LOG1P).factor(0.1)
      )
      .boostMode("sum")
  }

  def total(queryString: String): Future[Long] = {
    esClient.execute {
      search(indexName / projectsCollection)
        .query(getQuery(SearchParams(queryString = queryString)))
        .size(0)
    }.map(_.totalHits)
  }

  def find(params: SearchParams): Future[(Pagination, List[Project])] = {
    query(getQuery(params), params)
  }

  def releases(project: Project.Reference, selection: ReleaseSelection): Future[List[Release]] = {
    esClient.execute {
      search(indexName / releasesCollection)
        .query(
          nestedQuery("reference").query(
            boolQuery().must(
              List(
                termQuery("reference.organization", project.organization),
                termQuery("reference.repository", project.repository)
              )
            )
          )
        )
        .size(5000)
    }.map(_.to[Release].toList)
  }

  /**
    * search for a maven artifact
    * @param maven
    * @return
    */
  def maven(maven: MavenReference): Future[Option[Release]] = {

    esClient.execute {
      search(indexName / releasesCollection)
        .query(
          nestedQuery("maven").query(
            boolQuery().must(
              termQuery("maven.groupId", maven.groupId),
              termQuery("maven.artifactId", maven.artifactId),
              termQuery("maven.version", maven.version)
            )
          )
        )
        .limit(1)
    }.map(r => r.to[Release].headOption)
  }

  def project(project: Project.Reference): Future[Option[Project]] = {
    esClient.execute {
      search(indexName / projectsCollection)
        .query(
          boolQuery().must(
            termQuery("organization", project.organization),
            termQuery("repository", project.repository)
          )
        )
        .limit(1)
    }.map(_.to[Project].headOption)
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
        val updatedProject = liveUpdate(project)
        DefaultRelease(updatedProject.repository,
                       selection,
                       releases.toSet,
                       updatedProject.defaultArtifact,
                       updatedProject.defaultStableVersion).map(sel => (updatedProject, sel))
      }
      case None => None
    }
  }

  private def liveUpdate(project: Project): Project = {
    val repo = new GithubRepo(project.organization, project.repository)
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    new GithubDownload(paths).run(repo, true, true, false)
    val github = GithubReader(paths, repo)
    val updatedProject = project.copy(
      github = github
    )
    esClient
      .execute(
        update(project.id.get).in(indexName / projectsCollection).doc(updatedProject)
      )
    updatedProject
  }

  def updateProject(projectRef: Project.Reference, form: ProjectForm): Future[Boolean] = {
    for {
      updatedProject <- project(projectRef).map(_.map(p => form.update(p)))
      ret <- updatedProject.flatMap { project =>
        project.id.map { id =>
          val esUpdate =
            esClient.execute(update(id) in (indexName / projectsCollection) doc project)

          logger.info("Updating live data on the index repository")
          val indexUpdate = SaveLiveData.saveProject(project, paths)

          esUpdate.zip(indexUpdate).map(_ => true)
        }
      }.getOrElse(Future.successful(false))
    } yield ret
  }

  private val frontPageCount = 12

  def latestProjects() =
    latest[Project](projectsCollection, "created", frontPageCount).map(_.map(hideId))
  def latestReleases() = latest[Release](releasesCollection, "released", frontPageCount)

  def mostDependedUpon() = {
    esClient.execute {
      search(indexName / projectsCollection)
        .query(matchAllQuery)
        .limit(frontPageCount)
        .sortBy(sortQuery(Some("dependentCount")))
    }.map(_.to[Project].toList)
  }

  private def latest[T: HitReader: Manifest](collection: String, by: String, n: Int): Future[List[T]] = {
    esClient.execute {
      search(indexName / collection)
        .query(
          bool(
            mustQueries = Nil,
            shouldQueries = Nil,
            notQueries = List(termQuery("deprecated", true))
          )
        )
        .sortBy(fieldSort(by).order(SortOrder.DESC))
        .limit(n)
    }.map(r => r.to[T].toList)
  }

  def topics(params: SearchParams = SearchParams()): Future[List[(String, Long)]] = {
    stringAggregations("github.topics", params).map(addParamsIfMissing(params.topics))
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

  def totalProjects(): Future[Long] = {
    esClient.execute {
      search(indexName / projectsCollection)
    }.map(_.totalHits)
  }

  def totalReleases(): Future[Long] = {
    esClient.execute {
      search(indexName / releasesCollection)
    }.map(_.totalHits)
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
      search(indexName / projectsCollection)
        .query(getQuery(params))
        .aggregations(termsAggregation(aggregationName).field(field).size(50))
    }.map(resp => {
      try {
        val agg = resp.aggregations.stringTermsResult(aggregationName)
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
}
