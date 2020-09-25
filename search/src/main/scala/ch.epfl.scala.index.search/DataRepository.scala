package ch.epfl.scala.index.search

import java.io.File

import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc.{Pagination, _}
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.search.mapping._
import com.sksamuel.elastic4s.bulk.RichBulkResponse
import com.sksamuel.elastic4s.embedded.LocalNode
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.SortDefinition
import com.sksamuel.elastic4s.{
  ElasticDsl,
  ElasticsearchClientUri,
  HitReader,
  TcpClient
}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.cluster.health.ClusterHealthStatus
import org.elasticsearch.common.lucene.search.function.CombineFunction
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier
import org.elasticsearch.search.sort.SortOrder
import resource.ManagedResource

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
 * @param esClient TCP client of the elasticsearch server
 */
class DataRepository(esClient: TcpClient,
                     indexName: String)(implicit ec: ExecutionContext)
    extends LazyLogging {
  import DataRepository._

  def waitUntilReady(): Unit = {
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
      val status = esClient.execute(clusterHealth()).await.getStatus
      status == ClusterHealthStatus.YELLOW ||
      status == ClusterHealthStatus.GREEN
    }
  }

  def close(): Unit = {
    esClient.close()
  }

  def deleteAll(): Future[Unit] = {
    for {
      exists <- esClient.execute(indexExists(indexName)).map(_.isExists())
      _ <- if (exists) esClient.execute(deleteIndex(indexName))
      else Future.successful(())
    } yield ()
  }

  def create(): Future[Unit] = {
    val request = createIndex(indexName)
      .analysis(DataMapping.englishReadme)
      .normalizers(DataMapping.lowercase)
      .mappings(
        mapping(projectsCollection).fields(DataMapping.projectFields: _*),
        mapping(releasesCollection).fields(DataMapping.releasesFields: _*),
        mapping(dependenciesCollection)
          .fields(DataMapping.dependenciesFields: _*)
      )

    esClient.execute(request).map(_ => ())
  }

  def insertProject(project: Project): Future[Unit] = {
    esClient
      .execute(
        indexInto(indexName / projectsCollection)
          .source(project)
      )
      .map(_ => ())
  }

  def updateProject(project: Project): Future[Unit] = {
    esClient
      .execute(
        update(project.id.get)
          .in(indexName / projectsCollection)
          .doc(project)
      )
      .map(_ => ())
  }

  def insertReleases(releases: Seq[Release]): Future[RichBulkResponse] = {
    val requests = releases.map { r =>
      indexInto(indexName / releasesCollection).source(ReleaseDocument(r))
    }

    esClient.execute(bulk(requests))
  }

  def insertRelease(release: Release): Future[Unit] = {
    esClient
      .execute {
        indexInto(indexName / releasesCollection)
          .source(ReleaseDocument(release))
      }
      .map(_ => ())
  }

  def insertDependencies(
      dependencies: Seq[ScalaDependency]
  ): Future[RichBulkResponse] = {
    if (dependencies.nonEmpty) {
      val requests = dependencies.map { d =>
        indexInto(indexName / dependenciesCollection)
          .source(DependencyDocument(d))
      }
      esClient.execute(bulk(requests))
    } else {
      Future.successful { emptyBulkResponse }
    }

  }

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
          result.to[Project].map(_.formatForDisplaying)
        )
      }
  }

  /**
   * Get all the releases of a particular project
   * It does not retrieve the dependencies of the releases
   */
  def getProjectReleases(project: Project.Reference): Future[Seq[Release]] = {
    val query = must(
      termQuery("reference.organization", project.organization),
      termQuery("reference.repository", project.repository)
    )

    val request = search(indexName / releasesCollection).query(query).size(5000)

    esClient
      .execute(request)
      .map(
        _.to[ReleaseDocument].map(_.toRelease).filter(_.isValid)
      )
  }

  /**
   * Search for the release corresponding to a maven artifact
   * It does not retrieve the dependencies of the release
   *
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

    esClient
      .execute(request)
      .map(r => r.to[ReleaseDocument].headOption.map(_.toRelease))
  }

  def getProject(project: Project.Reference): Future[Option[Project]] = {
    val query = must(
      termQuery("organization.keyword", project.organization),
      termQuery("repository.keyword", project.repository)
    )

    val request = search(indexName / projectsCollection).query(query).limit(1)

    esClient.execute(request).map(_.to[Project].headOption)
  }

  /**
   * Get a project and all its releases
   * It does not retrieve the dependencies of the releases
   */
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

  /**
   * Get a project and select a release
   * It does not retrieve the dependencies of the release
   */
  def getProjectAndReleaseOptions(
      ref: Project.Reference,
      selection: ReleaseSelection
  ): Future[Option[(Project, ReleaseOptions)]] = {
    getProjectAndReleases(ref).map {
      case Some((project, releases)) =>
        ReleaseOptions(
          project.repository,
          selection,
          releases,
          project.defaultArtifact,
          project.defaultStableVersion
        ).map(sel => (project, sel))
      case None => None
    }
  }

  /**
   * Get all the dependencies of a release
   */
  def getAllDependencies(ref: Release.Reference): Future[Seq[ScalaDependency]] = {
    val query = termQuery("dependentUrl", ref.httpUrl)

    val request =
      search(indexName / dependenciesCollection).query(query).size(5000)

    esClient
      .execute(request)
      .map(_.to[DependencyDocument].map(_.toDependency))
  }

  /**
   * Get all the releases which depend on a the given release
   */
  def getReverseDependencies(
      ref: Release.Reference
  ): Future[Seq[ScalaDependency]] = {
    val query = termQuery("targetUrl", ref.httpUrl)

    val request = search(indexName / dependenciesCollection)
      .query(query)
      .size(5000)

    esClient
      .execute(request)
      .map(_.to[DependencyDocument].map(_.toDependency))
  }

  def getLatestProjects(): Future[List[Project]] = {
    for {
      projects <- getLatest[Project](
        projectsCollection,
        "created",
        frontPageCount
      )
    } yield projects.map(_.formatForDisplaying)
  }

  def getLatestReleases(): Future[List[Release]] = {
    getLatest[ReleaseDocument](releasesCollection, "released", frontPageCount)
      .map(_.map(_.toRelease))
  }

  def getMostDependentUpon(): Future[List[Project]] = {
    val request = search(indexName / projectsCollection)
      .query(matchAllQuery())
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
    aggregations("scalaVersion", notDeprecatedQuery)
      .map(_.toList.sortBy(_._1))
  }

  def getScalaVersions(params: SearchParams): Future[List[(String, Long)]] = {
    aggregations("scalaVersion", filteredSearchQuery(params))
      .map(_.toList.sortBy(_._1))
      .map(addLabelsIfMissing(params.scalaVersions.toSet))
  }

  def getAllScalaJsVersions(): Future[List[(String, Long)]] = {
    versionAggregations("scalaJsVersion", notDeprecatedQuery, ScalaJs.isValid)
  }

  def getScalaJsVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations("scalaJsVersion",
                        filteredSearchQuery(params),
                        ScalaJs.isValid)
      .map(addLabelsIfMissing(params.scalaJsVersions.toSet))
  }

  def getAllScalaNativeVersions(): Future[List[(String, Long)]] = {
    versionAggregations("scalaNativeVersion",
                        notDeprecatedQuery,
                        ScalaNative.isValid)
  }

  def getScalaNativeVersions(
      params: SearchParams
  ): Future[List[(String, Long)]] = {
    versionAggregations(
      "scalaNativeVersion",
      filteredSearchQuery(params),
      ScalaNative.isValid
    ).map(addLabelsIfMissing(params.scalaNativeVersions.toSet))
  }

  def getAllSbtVersions(): Future[List[(String, Long)]] = {
    versionAggregations("sbtVersion", notDeprecatedQuery, SbtPlugin.isValid)
  }

  def getSbtVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations("sbtVersion",
                        filteredSearchQuery(params),
                        SbtPlugin.isValid)
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
      filterF: BinaryVersion => Boolean
  ): Future[List[(String, Long)]] = {

    aggregations(field, query).map { versionAgg =>
      val filteredAgg = for {
        (version, count) <- versionAgg.toList
        binaryVersion <- BinaryVersion.parse(version) if filterF(binaryVersion)
      } yield (binaryVersion, count)

      filteredAgg
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

object DataRepository extends LazyLogging with SearchProtocol with ElasticDsl {
  private val projectsCollection = "projects"
  private val releasesCollection = "releases"
  private val dependenciesCollection = "dependencies"
  private val emptyBulkResponse = RichBulkResponse(
    new BulkResponse(Array.empty, 0)
  )

  private lazy val config =
    ConfigFactory.load().getConfig("org.scala_lang.index.data")
  private lazy val elasticsearch = config.getString("elasticsearch")
  private lazy val indexName = config.getString("index")

  private lazy val local =
    if (elasticsearch == "remote") false
    else if (elasticsearch == "local" || elasticsearch == "local-prod") true
    else
      sys.error(
        s"org.scala_lang.index.data.elasticsearch should be remote or local: $elasticsearch"
      )

  def open(
      baseDirectory: File
  )(implicit ec: ExecutionContext): ManagedResource[DataRepository] = {
    logger.info(s"elasticsearch $elasticsearch $indexName")
    for (esClient <- resource.managed(esClient(baseDirectory, local))) yield {
      new DataRepository(esClient, indexName)
    }
  }

  def openUnsafe(
      baseDirectory: File
  )(implicit ec: ExecutionContext): DataRepository = {
    logger.info(s"elasticsearch $elasticsearch $indexName")
    new DataRepository(esClient(baseDirectory, local), indexName)
  }

  /** @see https://github.com/sksamuel/elastic4s#client for configurations */
  private def esClient(baseDirectory: File, local: Boolean): TcpClient = {
    if (local) {
      val homePath = baseDirectory.toPath.resolve(".esdata").toString
      LocalNode(
        LocalNode.requiredSettings(
          clusterName = "elasticsearch-local",
          homePath = homePath
        )
      ).elastic4sclient()
    } else {
      TcpClient.transport(ElasticsearchClientUri("localhost", 9300))
    }
  }

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
      case Some("relevant") => scoreSort() order SortOrder.DESC
      case Some("created")  => fieldSort("created") order SortOrder.DESC
      case Some("updated")  => fieldSort("updated") order SortOrder.DESC
      case _                => scoreSort() order SortOrder.DESC
    }

  private val notDeprecatedQuery: QueryDefinition = {
    not(termQuery("deprecated", true))
  }

  private def searchQuery(
      queryString: String,
      contributingSearch: Boolean
  ): QueryDefinition = {
    val (filters, plainText) =
      queryString
        .replace("/", "\\/")
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
    termQuery("github.topics.keyword", topic)
  }

  private val cliQuery = termQuery("hasCli", true)

  private def repositoriesQuery(
      repositories: Seq[GithubRepo]
  ): QueryDefinition = {
    should(repositories.map(repositoryQuery))
  }

  private def repositoryQuery(repo: GithubRepo): QueryDefinition = {
    must(
      termQuery("organization.keyword", repo.organization),
      termQuery("repository.keyword", repo.repository)
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
    target match {
      case ScalaJvm(scalaVersion) =>
        termQuery("scalaVersion", scalaVersion.toString)
      case ScalaJs(scalaVersion, jsVersion) =>
        must(
          termQuery("scalaVersion", scalaVersion.toString),
          termQuery("scalaJsVersion", jsVersion.toString)
        )
      case ScalaNative(scalaVersion, nativeVersion) =>
        must(
          termQuery("scalaVersion", scalaVersion.toString),
          termQuery("scalaNativeVersion", nativeVersion.toString)
        )
      case SbtPlugin(scalaVersion, sbtVersion) =>
        must(
          termQuery("scalaVersion", scalaVersion.toString),
          termQuery("sbtVersion", sbtVersion.toString)
        )
    }
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
    param.map(query).getOrElse(matchAllQuery())
  }
}
