package ch.epfl.scala.index
package server

import model._
import model.misc._
import data.DataPaths
import data.project.ProjectForm
import data.github.GithubDownload
import release._
import misc.Pagination
import data.elastic._
import com.sksamuel.elastic4s.HitReader
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.SortDefinition
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import org.elasticsearch.common.lucene.search.function.CombineFunction

/**
 * @param paths   Paths to the files storing the index
 */
class DataRepository(
    paths: DataPaths,
    githubDownload: GithubDownload
)(private implicit val ec: ExecutionContext) {
  import DataRepository._

  private def hideId(p: Project) = p.copy(id = None)

  private val log = LoggerFactory.getLogger(getClass)

  private val sortQuery: Option[String] => SortDefinition = {
    case Some("stars") =>
      fieldSort("github.stars") missing "0" order SortOrder.DESC // mode MultiMode.Avg
    case Some("forks") =>
      fieldSort("github.forks") missing "0" order SortOrder.DESC // mode MultiMode.Avg
    case Some("dependentCount") =>
      fieldSort("dependentCount") missing "0" order SortOrder.DESC // mode MultiMode.Avg
    case Some("contributors") =>
      fieldSort("github.contributorCount") missing "0" order SortOrder.DESC // mode MultiMode.Avg
    case Some("relevant") => scoreSort order SortOrder.DESC
    case Some("created")  => fieldSort("created") order SortOrder.DESC
    case Some("updated")  => fieldSort("updated") order SortOrder.DESC
    case _                => scoreSort order SortOrder.DESC
  }

  private val contributingQuery = boolQuery().must(
    Seq(
      nestedQuery(
        "github.beginnerIssues",
        existsQuery("github.beginnerIssues")
      ),
      existsQuery("github.contributingGuide"),
      existsQuery("github.chatroom")
    )
  )

  private def clamp(page: Int): Int =
    if (page <= 0) 1 else page

  private def targetFiltering(params: SearchParams): List[QueryDefinition] = {
    params.targetFiltering
      .map { target =>
        val scalaVersionFiltering =
          List(
            boolQuery().must(
              termQuery(
                "scalaVersion",
                target.scalaVersion.toString
              )
            )
          )

        val scalaJsVersionFiltering =
          target.scalaJsVersion
            .map(
              jsVersion =>
                List(
                  boolQuery().must(
                    termQuery(
                      "scalaJsVersion",
                      jsVersion.toString
                    )
                  )
              )
            )
            .getOrElse(Nil)

        val scalaNativeVersionFiltering =
          target.scalaNativeVersion
            .map(
              nativeVersion =>
                List(
                  boolQuery().must(
                    termQuery(
                      "scalaNativeVersion",
                      nativeVersion.toString
                    )
                  )
              )
            )
            .getOrElse(Nil)

        val sbtVersionFiltering =
          target.sbtVersion
            .map(
              sbtVersion =>
                List(
                  boolQuery().must(
                    termQuery(
                      "sbtVersion",
                      sbtVersion.toString
                    )
                  )
              )
            )
            .getOrElse(Nil)

        scalaVersionFiltering ++ scalaJsVersionFiltering ++ scalaNativeVersionFiltering ++ sbtVersionFiltering
      }
      .getOrElse(Nil)
  }

  private def targetsQuery(params: SearchParams): List[QueryDefinition] = {
    val targetTypes = params.targetTypes.map(
      targetType => boolQuery().must(termQuery("targetType", targetType))
    )

    val scalaVersions = params.scalaVersions.map(
      scalaVersion => boolQuery().must(termQuery("scalaVersion", scalaVersion))
    )

    val scalaJsVersions = params.scalaJsVersions.map(
      scalaJsVersion =>
        boolQuery().must(termQuery("scalaJsVersion", scalaJsVersion))
    )

    val scalaNativeVersions = params.scalaNativeVersions.map(
      scalaNativeVersion =>
        boolQuery().must(termQuery("scalaNativeVersion", scalaNativeVersion))
    )

    val sbtVersions = params.sbtVersions.map(
      sbtVersion => boolQuery().must(termQuery("sbtVersion", sbtVersion))
    )

    targetTypes ++ scalaVersions ++ scalaJsVersions ++ scalaNativeVersions ++ sbtVersions
  }

  private def getQuery(params: SearchParams): QueryDefinition = {
    val escapedQuery = params.queryString.replaceAllLiterally("/", "\\/")

    val cliQuery =
      if (params.cli) List(termQuery("hasCli", true))
      else Nil

    val topicsQuery =
      params.topics.map(
        topic => boolQuery().should(termQuery("github.topics", topic))
      )

    val contributingSearchQuery =
      if (params.contributingSearch) {
        List(contributingQuery)
      } else {
        Nil
      }

    val mustQueriesRepos: List[QueryDefinition] = {
      if (params.userRepos.nonEmpty) {
        val reposQueries =
          params.userRepos.toList.map {
            case GithubRepo(organization, repository) =>
              boolQuery().must(
                termQuery("organization", organization.toLowerCase),
                termQuery("repository", repository.toLowerCase)
              )
          }
        List(boolQuery().should(reposQueries: _*))
      } else Nil
    }

    val luceneQueries = escapedQuery.split(" ").flatMap { subQuery =>
      if (subQuery.contains(":")) Some(luceneQueryDef(subQuery))
      else None
    }

    val searchQuery =
      if (escapedQuery.isEmpty || escapedQuery == "*") None
      else
        Some {
          boolQuery.should(
            multiMatchQuery(escapedQuery)
              .field("repository", 6)
              .field("primaryTopic", 5)
              .field("organization", 5)
              .field("repository.analyzed", 4)
              .field("primaryTopic.analyzed", 4)
              .field("github.description", 4)
              .field("github.topics", 4)
              .field("github.topics.analyzed", 2)
              .field("artifacts", 2)
              .field("organization.analyzed", 1),
            matchQuery("github.readme", escapedQuery).boost(0.5),
            nestedQuery(
              "github.beginnerIssues",
              matchQuery("github.beginnerIssues.title", escapedQuery)
            ).inner(innerHits("issues").size(7))
              .boost {
                if (params.contributingSearch) 8
                else 1
              }
          )
        }

    val queryDef = functionScoreQuery(
      boolQuery()
        .must(
          mustQueriesRepos ++
            cliQuery ++
            topicsQuery ++
            targetsQuery(params) ++
            targetFiltering(params) ++
            contributingSearchQuery ++
            luceneQueries ++
            searchQuery
        )
        .not(List(termQuery("deprecated", true)))
    ).scorers(
        fieldFactorScore("github.stars")
          .missing(0)
          .modifier(Modifier.LN2P)
      )
      .boostMode(CombineFunction.MULTIPLY)

    if (params.contributingSearch && !params.queryString.isEmpty)
      queryDef.minScore(15)
    else queryDef
  }

  def total(queryString: String): Future[Long] = {
    esClient
      .execute {
        search(indexName / projectsCollection)
          .query(getQuery(SearchParams(queryString = queryString)))
          .size(0)
      }
      .map(_.totalHits)
  }

  def find(params: SearchParams): Future[Page[Project]] = {
    val queryDef = getQuery(params)
    val sortDef = sortQuery(params.sorting)

    esClient
      .execute(
        search(indexName / projectsCollection)
          .query(queryDef)
          .sortBy(sortDef)
          .from(params.total * (clamp(params.page) - 1))
          .size(params.total)
      )
      .map { result =>
        Page(
          Pagination(
            current = clamp(params.page),
            pageCount =
              Math.ceil(result.totalHits / params.total.toDouble).toInt,
            itemCount = result.totalHits
          ),
          result.to[Project].map(hideId)
        )
      }
  }

  def queryIsTopic(queryString: String): Future[Boolean] = {

    val q = getQuery(SearchParams(topics = List(queryString)))

    esClient
      .execute {
        search(indexName / projectsCollection)
          .query(q)
          .size(0)
          .terminateAfter(1)
      }
      .map(_.totalHits > 0)
  }

  def releases(project: Project.Reference): Future[List[Release]] = {
    esClient
      .execute {
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
      }
      .map(_.to[Release].toList)
  }

  /**
   * search for a maven artifact
   * @param maven
   * @return
   */
  def maven(maven: MavenReference): Future[Option[Release]] = {

    esClient
      .execute {
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
      }
      .map(r => r.to[Release].headOption)
  }

  def project(project: Project.Reference): Future[Option[Project]] = {
    esClient
      .execute {
        search(indexName / projectsCollection)
          .query(
            boolQuery().must(
              termQuery("organization", project.organization),
              termQuery("repository", project.repository)
            )
          )
          .limit(1)
      }
      .map(_.to[Project].headOption)
  }

  def projectAndReleases(
      projectRef: Project.Reference
  ): Future[Option[(Project, List[Release])]] = {
    for {
      project <- project(projectRef)
      releases <- releases(projectRef)
    } yield project.map((_, releases))
  }

  def projectPage(
      projectRef: Project.Reference,
      selection: ReleaseSelection
  ): Future[Option[(Project, ReleaseOptions)]] = {
    projectAndReleases(projectRef).map {
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

  def updateProject(projectRef: Project.Reference,
                    form: ProjectForm): Future[Boolean] = {
    for {
      updatedProject <- project(projectRef).map(
        _.map(p => form.update(p, paths, githubDownload))
      )
      ret <- updatedProject
        .flatMap { project =>
          project.id.map { id =>
            val esUpdate =
              esClient.execute(
                update(id) in (indexName / projectsCollection) doc project
              )

            log.info("Updating live data on the index repository")
            val indexUpdate = SaveLiveData.saveProject(project, paths)

            esUpdate.zip(indexUpdate).map(_ => true)
          }
        }
        .getOrElse(Future.successful(false))
    } yield ret
  }

  private val frontPageCount = 12

  def latestProjects() =
    latest[Project](projectsCollection, "created", frontPageCount)
      .map(_.map(hideId))

  def latestReleases() =
    latest[Release](releasesCollection, "released", frontPageCount)

  def mostDependedUpon(): Future[List[Project]] = {
    esClient
      .execute {
        search(indexName / projectsCollection)
          .query(matchAllQuery)
          .limit(frontPageCount)
          .sortBy(sortQuery(Some("dependentCount")))
      }
      .map(_.to[Project].toList)
  }

  private def latest[T: HitReader: Manifest](collection: String,
                                             by: String,
                                             n: Int): Future[List[T]] = {
    esClient
      .execute {
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
      }
      .map(r => r.to[T].toList)
  }

  def topics(
      params: Option[SearchParams] = None
  ): Future[List[(String, Long)]] = {
    stringAggregations("github.topics", params).map(
      addParamsIfMissing(params, _.topics)
    )
  }

  def targetTypes(
      params: Option[SearchParams] = None
  ): Future[List[(String, String, Long)]] = {
    def labelize(in: String): String = {
      if (in == "JVM") "Scala (Jvm)"
      else in.take(1).map(_.toUpper) + in.drop(1).map(_.toLower)
    }

    stringAggregations("targetType", params)
      .map(addParamsIfMissing(params, _.targetTypes))
      .map(_.map {
        case (targetType, count) => (targetType, labelize(targetType), count)
      })
  }

  private def stringAggregations(
      field: String,
      params: Option[SearchParams] = None
  ): Future[List[(String, Long)]] = {
    aggregations(field, params).map(_.toList.sortBy(_._1).toList)
  }

  def scalaVersions(
      params: Option[SearchParams] = None
  ): Future[List[(String, Long)]] = {
    val minVer = SemanticVersion(2, 10)
    val maxVer = SemanticVersion(2, 13)

    versionAggregations("scalaVersion",
                        params,
                        version => minVer <= version && version <= maxVer)
      .map(addParamsIfMissing(params, _.scalaVersions))
  }

  def scalaJsVersions(
      params: Option[SearchParams] = None
  ): Future[List[(String, Long)]] = {
    versionAggregations("scalaJsVersion", params, _ => true)
      .map(addParamsIfMissing(params, _.scalaJsVersions))
  }

  def scalaNativeVersions(
      params: Option[SearchParams] = None
  ): Future[List[(String, Long)]] = {
    versionAggregations("scalaNativeVersion", params, _ => true)
      .map(addParamsIfMissing(params, _.scalaNativeVersions))
  }

  def sbtVersions(
      params: Option[SearchParams] = None
  ): Future[List[(String, Long)]] = {
    versionAggregations("sbtVersion", params, _ => true)
      .map(addParamsIfMissing(params, _.sbtVersions))
  }

  def totalProjects(): Future[Long] = {
    esClient
      .execute {
        search(indexName / projectsCollection)
      }
      .map(_.totalHits)
  }

  def totalReleases(): Future[Long] = {
    esClient
      .execute {
        search(indexName / releasesCollection)
      }
      .map(_.totalHits)
  }

  def contributingProjects(): Future[List[Project]] = {
    esClient
      .execute {
        search(indexName / projectsCollection)
          .query(
            functionScoreQuery(contributingQuery)
              .scorers(randomScore(scala.util.Random.nextInt(10000)))
              .boostMode("sum")
          )
          .limit(frontPageCount)
      }
      .map(_.to[Project].toList)
  }

  private def addParamsIfMissing(p: Option[SearchParams],
                                 f: SearchParams => List[String])(
      result: List[(String, Long)]
  ): List[(String, Long)] = {
    p match {
      case Some(params) => {
        val pSet = f(params).toSet
        val rSet = result.map(_._1).toSet

        val diff = pSet -- rSet

        if (diff.nonEmpty) {
          (diff.toList.map(label => (label, 0L)) ++ result).sortBy(_._1)
        } else result
      }
      case _ => result
    }
  }

  private def versionAggregations(
      field: String,
      params: Option[SearchParams],
      filterF: SemanticVersion => Boolean
  ): Future[List[(String, Long)]] = {

    def sortedByVersion(aggregation: Map[String, Long]): List[(String, Long)] = {
      aggregation.toList
        .flatMap {
          case (version, count) =>
            SemanticVersion(version).map(v => (v, count))
        }
        .filter { case (version, _) => filterF(version) }
        .groupBy {
          case (version, _) => SemanticVersion(version.major, version.minor)
        }
        .mapValues(_.map(_._2).sum)
        .toList
        .sortBy(_._1)
        .map { case (v, c) => (v.toString, c) }
    }

    aggregations(field, params).map(sortedByVersion)
  }

  private def aggregations(
      field: String,
      params: Option[SearchParams]
  ): Future[Map[String, Long]] = {
    import scala.collection.JavaConverters._
    val aggregationName = s"${field}_count"

    val q = params.map(getQuery).getOrElse(matchAllQuery)

    esClient
      .execute {
        search(indexName / projectsCollection)
          .query(q)
          .aggregations(
            termsAggregation(aggregationName)
              .field(field)
              .size(50)
          )
      }
      .map(resp => {
        try {
          val agg = resp.aggregations.stringTermsResult(aggregationName)
          agg.getBuckets.asScala.toList.collect {
            case b: StringTerms.Bucket => b.getKeyAsString -> b.getDocCount
          }.toMap
        } catch {
          case e: Exception => {
            log.error("failed aggregations", e)
            Map()
          }
        }
      })
  }

  private lazy val cachedTopics: Set[String] = {
    import scala.collection.JavaConverters._

    val aggregationName = "topic_count"

    Await.result(
      esClient
        .execute {
          search(indexName / projectsCollection)
            .aggregations(
              termsAggregation(aggregationName).field("github.topics")
            )
        }
        .map(resp => {
          try {
            val agg = resp.aggregations.stringTermsResult(aggregationName)
            agg.getBuckets.asScala.toList.collect {
              case b: StringTerms.Bucket => b.getKeyAsString
            }.toSet
          } catch {
            case e: Exception => {
              log.error("failed aggregations", e)
              Set()
            }
          }
        }),
      Duration.Inf
    )
  }
}

object DataRepository {
  private val fieldMapping = Map(
    "depends-on" -> "dependencies",
    "topics" -> "github.topics"
  )

  private def replaceFields(queryString: String) = {
    fieldMapping.foldLeft(queryString) {
      case (query, (input, replacement)) =>
        val regex = s"(\\s|^)$input:".r
        regex.replaceAllIn(query, s"$$1$replacement:")
    }
  }

  /**
   * Treats the query inputted by a user as a lucene query
   *
   * @param queryString the query inputted by user
   * @return the elastic query definition
   */
  private def luceneQueryDef(queryString: String): QueryDefinition = {
    stringQuery(
      replaceFields(queryString)
    )
  }
}
