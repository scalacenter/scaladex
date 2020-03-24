package ch.epfl.scala.index
package server

import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.elastic._
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.project.ProjectForm
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc.{Pagination, _}
import ch.epfl.scala.index.model.release._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.HitReader
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.SortDefinition
import org.elasticsearch.common.lucene.search.function.CombineFunction
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import DataRepository._
import scala.collection.JavaConverters._

import scala.concurrent.{ExecutionContext, Future}

/**
 * @param paths   Paths to the files storing the index
 */
class DataRepository(paths: DataPaths, githubDownload: GithubDownload)(
    implicit ec: ExecutionContext
) {

  private val log = LoggerFactory.getLogger(getClass)

  def getTotalProjects(queryString: String): Future[Long] = {
    val query = must(
      notDeprecatedQuery,
      searchQuery(queryString, contributingSearch = false)
    )
    val request = search(indexName / projectsCollection).query(query).size(0)
    esClient.execute(request).map(_.totalHits)
  }

  def findProjects(params: SearchParams): Future[Page[Project]] = {
    def clamp(page: Int): Int = if (page <= 0) 1 else page

    val request = search(indexName / projectsCollection)
      .query(gitHubStarScoring(fullQuery(params)))
      .sortBy(sortQuery(params.sorting))
      .from(params.total * (clamp(params.page) - 1))
      .size(params.total)

    esClient
      .execute(request)
      .map { result =>
        Page(
          Pagination(
            current = clamp(params.page),
            pageCount =
              Math.ceil(result.totalHits / params.total.toDouble).toInt,
            itemCount = result.totalHits
          ),
          result.to[Project].map(_.hideId)
        )
      }
  }

  def isTopic(topic: String): Future[Boolean] = {
    val query = must(notDeprecatedQuery, topicQuery(topic))

    val request = search(indexName / projectsCollection)
      .query(query)
      .size(0)
      .terminateAfter(1)

    esClient.execute(request).map(_.totalHits > 0)
  }

  def getProjectReleases(project: Project.Reference): Future[Seq[Release]] = {
    val query = nestedQuery("reference").query(
      must(
        termQuery("reference.organization", project.organization),
        termQuery("reference.repository", project.repository)
      )
    )

    val request = search(indexName / releasesCollection).query(query).size(5000)

    esClient.execute(request).map(_.to[Release])
  }

  /**
   * search for a maven artifact
   * @param reference reference of the maven artifact
   * @return the release of this artifact if it exists
   */
  def getMavenArtifact(reference: MavenReference): Future[Option[Release]] = {
    val query = nestedQuery("maven").query(
      must(
        termQuery("maven.groupId", reference.groupId),
        termQuery("maven.artifactId", reference.artifactId),
        termQuery("maven.version", reference.version)
      )
    )

    val request = search(indexName / releasesCollection).query(query).limit(1)

    esClient.execute(request).map(r => r.to[Release].headOption)
  }

  def getProject(project: Project.Reference): Future[Option[Project]] = {
    val query = must(
      termQuery("organization", project.organization),
      termQuery("repository", project.repository)
    )

    val request = search(indexName / projectsCollection).query(query).limit(1)

    esClient.execute(request).map(_.to[Project].headOption)
  }

  def getProjectAndReleases(
      projectRef: Project.Reference
  ): Future[Option[(Project, Seq[Release])]] = {
    val projectF = getProject(projectRef)
    val projectReleaseF = getProjectReleases(projectRef)

    for {
      project <- projectF
      releases <- projectReleaseF
    } yield project.map((_, releases))
  }

  def getProjectPage(
      ref: Project.Reference,
      selection: ReleaseSelection
  ): Future[Option[(Project, ReleaseOptions)]] = {
    getProjectAndReleases(ref).map {
      case Some((project, releases)) =>
        DefaultRelease(
          project.repository,
          selection,
          releases.toSet,
          project.defaultArtifact,
          project.defaultStableVersion
        ).map(sel => (project, sel))
      case None => None
    }
  }

  def updateProject(projectRef: Project.Reference,
                    form: ProjectForm): Future[Boolean] = {
    for {
      projectOpt <- getProject(projectRef)
      updated <- projectOpt match {
        case Some(project) if project.id.isDefined =>
          val updatedProject = form.update(project, paths, githubDownload)
          val esUpdate = esClient.execute(
            update(project.id.get)
              .in(indexName / projectsCollection)
              .doc(updatedProject)
          )

          log.info("Updating live data on the index repository")
          val indexUpdate = SaveLiveData.saveProject(updatedProject, paths)

          esUpdate.zip(indexUpdate).map(_ => true)
        case _ => Future.successful(false)
      }
    } yield updated
  }

  def getLatestProjects(): Future[List[Project]] = {
    for {
      projects <- getLatest[Project](
        projectsCollection,
        "created",
        frontPageCount
      )
    } yield projects.map(_.hideId)
  }

  def getLatestReleases(): Future[List[Release]] = {
    getLatest[Release](releasesCollection, "released", frontPageCount)
  }

  def getMostDependentUpon(): Future[List[Project]] = {
    val request = search(indexName / projectsCollection)
      .query(matchAllQuery)
      .limit(frontPageCount)
      .sortBy(sortQuery(Some("dependentCount")))
    esClient
      .execute(request)
      .map(_.to[Project].toList)
  }

  private def getLatest[T: HitReader: Manifest](collection: String,
                                                sortingField: String,
                                                size: Int): Future[List[T]] = {
    val request = search(indexName / collection)
      .query(notDeprecatedQuery)
      .sortBy(fieldSort(sortingField).order(SortOrder.DESC))
      .limit(size)

    esClient.execute(request).map(r => r.to[T].toList)
  }

  def getAllTopics(): Future[List[(String, Long)]] = {
    stringAggregations("github.topics", notDeprecatedQuery)
  }

  def getTopics(params: SearchParams): Future[List[(String, Long)]] = {
    stringAggregations("github.topics", fullQuery(params))
      .map(addLabelsIfMissing(params.topics.toSet))
  }

  def getAllTargetTypes(): Future[List[(String, String, Long)]] = {
    stringAggregations("targetType", notDeprecatedQuery)
      .map(_.map {
        case (targetType, count) =>
          (targetType, labelizeTargetType(targetType), count)
      })
  }

  def getTargetTypes(
      params: SearchParams
  ): Future[List[(String, String, Long)]] = {
    stringAggregations("targetType", fullQuery(params))
      .map(addLabelsIfMissing(params.targetTypes.toSet))
      .map(_.map {
        case (targetType, count) =>
          (targetType, labelizeTargetType(targetType), count)
      })
  }

  private def stringAggregations(
      field: String,
      query: QueryDefinition
  ): Future[List[(String, Long)]] = {
    aggregations(field, query).map(_.toList.sortBy(_._1).toList)
  }

  def allScalaVersions(): Future[List[(String, Long)]] = {
    versionAggregations("scalaVersion", notDeprecatedQuery, filterScalaVersion)
  }

  def scalaVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations("scalaVersion", fullQuery(params), filterScalaVersion)
      .map(addLabelsIfMissing(params.scalaVersions.toSet))
  }

  def allScalaJsVersions(): Future[List[(String, Long)]] = {
    versionAggregations("scalaJsVersion", notDeprecatedQuery, _ => true)
  }

  def scalaJsVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations("scalaJsVersion", fullQuery(params), _ => true)
      .map(addLabelsIfMissing(params.scalaJsVersions.toSet))
  }

  def allScalaNativeVersions(): Future[List[(String, Long)]] = {
    versionAggregations("scalaNativeVersion", notDeprecatedQuery, _ => true)
  }

  def scalaNativeVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations("scalaNativeVersion", fullQuery(params), _ => true)
      .map(addLabelsIfMissing(params.scalaNativeVersions.toSet))
  }

  def allSbtVersions(): Future[List[(String, Long)]] = {
    versionAggregations("sbtVersion", notDeprecatedQuery, _ => true)
  }

  def sbtVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations("sbtVersion", fullQuery(params), _ => true)
      .map(addLabelsIfMissing(params.sbtVersions.toSet))
  }

  def totalProjects(): Future[Long] = {
    esClient
      .execute(search(indexName / projectsCollection))
      .map(_.totalHits)
  }

  def totalReleases(): Future[Long] = {
    esClient
      .execute(search(indexName / releasesCollection))
      .map(_.totalHits)
  }

  def contributingProjects(): Future[List[Project]] = {
    val request = search(indexName / projectsCollection)
      .query(
        functionScoreQuery(contributingQuery)
          .scorers(randomScore(scala.util.Random.nextInt(10000)))
          .boostMode("sum")
      )
      .limit(frontPageCount)

    esClient
      .execute(request)
      .map(_.to[Project].toList)
  }

  private def versionAggregations(
      field: String,
      query: QueryDefinition,
      filterF: SemanticVersion => Boolean
  ): Future[List[(String, Long)]] = {

    aggregations(field, query).map { versionAgg =>
      val filteredAgg = for {
        (version, count) <- versionAgg.toList
        semanticVersion <- SemanticVersion(version) if filterF(semanticVersion)
      } yield (semanticVersion, count)

      filteredAgg
        .groupBy {
          case (version, _) => SemanticVersion(version.major, version.minor)
        }
        .mapValues(group => group.map { case (_, count) => count }.sum)
        .toList
        .sortBy(_._1)
        .map { case (v, c) => (v.toString, c) }
    }
  }

  private def aggregations(
      field: String,
      query: QueryDefinition
  ): Future[Map[String, Long]] = {
    val aggregationName = s"${field}_count"

    val aggregation = termsAggregation(aggregationName).field(field).size(50)

    val request = search(indexName / projectsCollection)
      .query(query)
      .aggregations(aggregation)

    for (response <- esClient.execute(request)) yield {
      response.aggregations
        .stringTermsResult(aggregationName)
        .getBuckets
        .asScala
        .map { bucket =>
          bucket.getKeyAsString -> bucket.getDocCount
        }
        .toMap
    }
  }
}

object DataRepository {
  private val frontPageCount = 12
  private val minScalaVersion = SemanticVersion(2, 10)
  private val maxScalaVersion = SemanticVersion(2, 13)

  private def filterScalaVersion(version: SemanticVersion): Boolean = {
    minScalaVersion <= version && version <= maxScalaVersion
  }

  private def labelizeTargetType(targetType: String): String = {
    if (targetType == "JVM") "Scala (Jvm)"
    else targetType.take(1).map(_.toUpper) + targetType.drop(1).map(_.toLower)
  }

  private val notDeprecatedQuery: QueryDefinition = {
    boolQuery().not(termQuery("deprecated", true))
  }

  private def searchQuery(
      queryString: String,
      contributingSearch: Boolean
  ): QueryDefinition = {
    val escapedQuery = queryString.replaceAllLiterally("/", "\\/")

    val luceneQuery = must(
      escapedQuery.split(" ").flatMap { subQuery =>
        if (subQuery.contains(":")) Some(luceneQueryDef(subQuery))
        else None
      }
    )

    val textQuery = {
      if (escapedQuery.isEmpty || escapedQuery == "*") matchAllQuery()
      else {
        val multiMatch = multiMatchQuery(escapedQuery)
          .field("repository", 6)
          .field("primaryTopic", 5)
          .field("organization", 5)
          .field("repository.analyzed", 4)
          .field("primaryTopic.analyzed", 4)
          .field("github.description", 4)
          .field("github.topics", 4)
          .field("github.topics.analyzed", 2)
          .field("artifacts", 2)
          .field("organization.analyzed", 1)

        val readmeMatch = matchQuery("github.readme", escapedQuery).boost(0.5)

        val contributingQuery = nestedQuery(
          "github.beginnerIssues",
          matchQuery("github.beginnerIssues.title", escapedQuery)
        ).inner(innerHits("issues").size(7))
          .boost(if (contributingSearch) 8 else 1)

        should(
          multiMatch,
          readmeMatch,
          contributingQuery
        )
      }
    }

    must(luceneQuery, textQuery)
  }

  private def topicsQuery(topics: Seq[String]): QueryDefinition = {
    must(topics.map(topicQuery))
  }

  private def topicQuery(topic: String): QueryDefinition = {
    termQuery("github.topics", topic)
  }

  private val cliQuery = termQuery("hasCli", true)

  private def repositoriesQuery(
      repositories: Seq[GithubRepo]
  ): QueryDefinition = {
    should(repositories.map(repositoryQuery))
  }

  private def repositoryQuery(repo: GithubRepo): QueryDefinition = {
    must(
      termQuery("organization", repo.organization),
      termQuery("repository", repo.repository)
    )
  }

  private def optionalQuery(condition: Boolean,
                            query: QueryDefinition): QueryDefinition = {
    if (condition) query else matchAllQuery()
  }

  private def optionalQuery[P](
      param: Option[P]
  )(query: P => QueryDefinition): QueryDefinition = {
    param.map(query).getOrElse(matchAllQuery)
  }

  private def gitHubStarScoring(query: QueryDefinition): QueryDefinition = {
    val scorer = fieldFactorScore("github.stars")
      .missing(0)
      .modifier(Modifier.LN2P)
    functionScoreQuery(query)
      .scorers(scorer)
      .boostMode(CombineFunction.MULTIPLY)
  }

  private def fullQuery(params: SearchParams): QueryDefinition = {
    must(
      notDeprecatedQuery,
      repositoriesQuery(params.userRepos.toSeq),
      optionalQuery(params.cli, cliQuery),
      topicsQuery(params.topics),
      targetsQuery(
        params.targetTypes,
        params.scalaVersions,
        params.scalaJsVersions,
        params.scalaNativeVersions,
        params.sbtVersions
      ),
      optionalQuery(params.targetFiltering)(targetQuery),
      optionalQuery(params.contributingSearch, contributingQuery),
      searchQuery(params.queryString, params.contributingSearch)
    )
  }

  private def targetsQuery(
      targetTypes: Seq[String],
      scalaVersions: Seq[String],
      scalaJsVersions: Seq[String],
      scalaNativeVersions: Seq[String],
      sbtVersions: Seq[String]
  ): QueryDefinition = {
    must(
      targetTypes.map(termQuery("targetType", _)) ++
        scalaVersions.map(termQuery("scalaVersion", _)) ++
        scalaJsVersions.map(termQuery("scalaJsVersion", _)) ++
        scalaNativeVersions.map(termQuery("scalaNativeVersion", _)) ++
        sbtVersions.map(termQuery("sbtVersion", _))
    )
  }

  private def targetQuery(target: ScalaTarget): QueryDefinition = {
    must(
      termQuery("scalaVersion", target.scalaVersion.toString),
      optionalQuery(target.scalaJsVersion) { version =>
        termQuery("scalaJsVersion", version.toString)
      },
      optionalQuery(target.scalaNativeVersion) { version =>
        termQuery("scalaNativeVersion", version.toString)
      },
      optionalQuery(target.sbtVersion) { version =>
        termQuery("sbtVersion", version.toString)
      }
    )
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

  private def sortQuery(sorting: Option[String]): SortDefinition =
    sorting match {
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

  private def addLabelsIfMissing(
      labelSet: Set[String]
  )(result: List[(String, Long)]): List[(String, Long)] = {
    val missingLabels = labelSet -- result.map { case (label, _) => label }
      .toSet

    (result ++ missingLabels.map(label => (label, 0L))).sortBy {
      case (label, _) => label
    }
  }
}
