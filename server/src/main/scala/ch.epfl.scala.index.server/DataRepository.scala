package ch.epfl.scala.index
package server

import ch.epfl.scala.index.data.DataPaths
import ch.epfl.scala.index.data.elastic._
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.project.ProjectForm
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc.{Pagination, _}
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.server.DataRepository._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.HitReader
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.SortDefinition
import org.elasticsearch.common.lucene.search.function.CombineFunction
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory

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

  def autocompleteProjects(params: SearchParams): Future[Seq[Project]] = {
    val request = search(indexName / projectsCollection)
      .query(gitHubStarScoring(filteredSearchQuery(params)))
      .sortBy(sortQuery(params.sorting))
      .limit(5)

    esClient
      .execute(request)
      .map(_.to[Project])
  }

  def findProjects(params: SearchParams): Future[Page[Project]] = {
    def clamp(page: Int): Int = if (page <= 0) 1 else page

    val request = search(indexName / projectsCollection)
      .query(gitHubStarScoring(filteredSearchQuery(params)))
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
      termQuery("organization.keyword", project.organization),
      termQuery("repository.keyword", project.repository)
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

  def getAllTopics(): Future[List[(String, Long)]] = {
    stringAggregations("github.topics.keyword", notDeprecatedQuery)
  }

  def getTopics(params: SearchParams): Future[List[(String, Long)]] = {
    stringAggregations("github.topics.keyword", filteredSearchQuery(params))
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
    stringAggregations("targetType", filteredSearchQuery(params))
      .map(addLabelsIfMissing(params.targetTypes.toSet))
      .map(_.map {
        case (targetType, count) =>
          (targetType, labelizeTargetType(targetType), count)
      })
  }

  def getAllScalaVersions(): Future[List[(String, Long)]] = {
    versionAggregations("scalaVersion", notDeprecatedQuery, filterScalaVersion)
  }

  def getScalaVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations("scalaVersion",
                        filteredSearchQuery(params),
                        filterScalaVersion)
      .map(addLabelsIfMissing(params.scalaVersions.toSet))
  }

  def getAllScalaJsVersions(): Future[List[(String, Long)]] = {
    versionAggregations("scalaJsVersion", notDeprecatedQuery, _ => true)
  }

  def getScalaJsVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations("scalaJsVersion",
                        filteredSearchQuery(params),
                        _ => true)
      .map(addLabelsIfMissing(params.scalaJsVersions.toSet))
  }

  def getAllScalaNativeVersions(): Future[List[(String, Long)]] = {
    versionAggregations("scalaNativeVersion", notDeprecatedQuery, _ => true)
  }

  def getScalaNativeVersions(
      params: SearchParams
  ): Future[List[(String, Long)]] = {
    versionAggregations("scalaNativeVersion",
                        filteredSearchQuery(params),
                        _ => true)
      .map(addLabelsIfMissing(params.scalaNativeVersions.toSet))
  }

  def getAllSbtVersions(): Future[List[(String, Long)]] = {
    versionAggregations("sbtVersion", notDeprecatedQuery, _ => true)
  }

  def getSbtVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations("sbtVersion", filteredSearchQuery(params), _ => true)
      .map(addLabelsIfMissing(params.sbtVersions.toSet))
  }

  def getTotalProjects(): Future[Long] = {
    esClient
      .execute(search(indexName / projectsCollection))
      .map(_.totalHits)
  }

  def getTotalReleases(): Future[Long] = {
    esClient
      .execute(search(indexName / releasesCollection))
      .map(_.totalHits)
  }

  def getContributingProjects(): Future[List[Project]] = {
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

  private def getLatest[T: HitReader: Manifest](collection: String,
                                                sortingField: String,
                                                size: Int): Future[List[T]] = {
    val request = search(indexName / collection)
      .query(notDeprecatedQuery)
      .sortBy(fieldSort(sortingField).order(SortOrder.DESC))
      .limit(size)

    esClient.execute(request).map(r => r.to[T].toList)
  }

  private def stringAggregations(
      field: String,
      query: QueryDefinition
  ): Future[List[(String, Long)]] = {
    aggregations(field, query).map(_.toList.sortBy(_._1).toList)
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
  private def gitHubStarScoring(query: QueryDefinition): QueryDefinition = {
    val scorer = fieldFactorScore("github.stars")
      .missing(0)
      .modifier(Modifier.LN2P)
    functionScoreQuery(query)
      .scorers(scorer)
      .boostMode(CombineFunction.MULTIPLY)
  }

  private def filteredSearchQuery(params: SearchParams): QueryDefinition = {
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

  private val notDeprecatedQuery: QueryDefinition = {
    boolQuery().not(termQuery("deprecated", true))
  }

  private def searchQuery(
      queryString: String,
      contributingSearch: Boolean
  ): QueryDefinition = {
    val (filters, plainText) =
      queryString
        .replaceAllLiterally("/", "\\/")
        .split(" AND ")
        .partition(_.contains(":")) match {
        case (luceneQueries, plainTerms) =>
          (luceneQueries.mkString(" AND "), plainTerms.mkString(" "))
      }

    val filterQuery = optionalQuery(
      filters.nonEmpty,
      luceneQuery(filters)
    )

    val plainTextQuery = {
      if (plainText.isEmpty || plainText == "*") matchAllQuery()
      else {
        val multiMatch = multiMatchQuery(plainText)
          .field("repository", 6)
          .field("primaryTopic", 5)
          .field("organization", 5)
          .field("github.description", 4)
          .field("github.topics", 4)
          .field("artifacts", 2)

        val readmeMatch = matchQuery("github.readme", plainText)
          .boost(0.5)

        val contributingQuery = {
          if (contributingSearch) {
            nestedQuery(
              "github.beginnerIssues",
              matchQuery("github.beginnerIssues.title", plainText)
            ).inner(innerHits("issues").size(7))
              .boost(4)
          } else matchNoneQuery()
        }

        val autocompleteQuery = plainText
          .split(" ")
          .lastOption
          .map { prefix =>
            dismax(
              prefixQuery("repository", prefix).boost(6),
              prefixQuery("primaryTopic", prefix).boost(5),
              prefixQuery("organization", prefix).boost(5),
              prefixQuery("github.description", prefix).boost(4),
              prefixQuery("github.topics", prefix).boost(4),
              prefixQuery("artifacts", prefix).boost(2)
            )
          }
          .getOrElse(matchNoneQuery())

        should(
          multiMatch,
          readmeMatch,
          autocompleteQuery,
          contributingQuery
        )
      }
    }

    must(filterQuery, plainTextQuery)
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

  /**
   * Treats the query inputted by a user as a lucene query
   *
   * @param queryString the query inputted by user
   * @return the elastic query definition
   */
  private def luceneQuery(queryString: String): QueryDefinition = {
    stringQuery(
      replaceFields(queryString)
    )
  }

  private val fieldMapping = Map(
    "depends-on" -> "dependencies",
    "topics" -> "github.topics.keyword",
    "organization" -> "organization.keyword",
    "primaryTopic" -> "primaryTopic.keyword",
    "repository" -> "repository.keyword"
  )

  private def replaceFields(queryString: String) = {
    fieldMapping.foldLeft(queryString) {
      case (query, (input, replacement)) =>
        val regex = s"(\\s|^)$input:".r
        regex.replaceAllIn(query, s"$$1$replacement:")
    }
  }

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

  private def addLabelsIfMissing(
      labelSet: Set[String]
  )(result: List[(String, Long)]): List[(String, Long)] = {
    val missingLabels = labelSet -- result.map { case (label, _) => label }
      .toSet

    (result ++ missingLabels.map(label => (label, 0L))).sortBy {
      case (label, _) => label
    }
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
}
