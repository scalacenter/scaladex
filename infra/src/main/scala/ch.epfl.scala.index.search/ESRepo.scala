package ch.epfl.scala.index.search

import java.io.Closeable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc.Pagination
import ch.epfl.scala.index.model.misc._
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.search.mapping._
import ch.epfl.scala.services.storage.sql.SqlRepo
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.ElasticProperties
import com.sksamuel.elastic4s.HitReader
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.bulk.BulkResponseItem
import com.sksamuel.elastic4s.requests.common.HealthStatus
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.requests.searches.aggs.responses.bucket.Terms
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.queries.funcscorer.CombineFunction
import com.sksamuel.elastic4s.requests.searches.queries.funcscorer.FieldValueFactorFunctionModifier
import com.sksamuel.elastic4s.requests.searches.sort.Sort
import com.sksamuel.elastic4s.requests.searches.sort.SortOrder
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

/**
 * @param esClient TCP client of the elasticsearch server
 */
class ESRepo(
    esClient: ElasticClient,
    indexPrefix: String
)(implicit
    ec: ExecutionContext
) extends LazyLogging
    with Closeable {
  import ESRepo._
  import ElasticDsl._

  private val projectIndex = s"$indexPrefix-projects"
  private val releaseIndex = s"$indexPrefix-releases"
  private val dependencyIndex = s"$indexPrefix-dependencies"

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
      val waitForYellowStatus =
        clusterHealth().waitForStatus(HealthStatus.Yellow)
      val response = esClient.execute(waitForYellowStatus).await
      response.isSuccess
    }
  }

  def close(): Unit = esClient.close()

  def deleteAll(): Future[Unit] = {
    def delete(index: String): Future[Unit] =
      for {
        response <- esClient.execute(indexExists(index))
        exist = response.result.isExists
        _ <-
          if (exist) esClient.execute(deleteIndex(index))
          else Future.successful(())
      } yield ()

    Future
      .sequence(Seq(projectIndex, releaseIndex, dependencyIndex).map(delete))
      .map(_ => ())
  }

  def create(): Future[Unit] = {
    import DataMapping._
    val createProject = createIndex(projectIndex)
      .analysis(
        Analysis(
          analyzers = List(englishReadme),
          charFilters = List(codeStrip, urlStrip),
          tokenFilters =
            List(englishStop, englishStemmer, englishPossessiveStemmer),
          normalizers = List(lowercase)
        )
      )
      .mapping(MappingDefinition(projectFields))

    val createRelease = createIndex(releaseIndex)
      .analysis(
        Analysis(
          analyzers = List(),
          normalizers = List(lowercase)
        )
      )
      .mapping(MappingDefinition(releasesFields))

    val createDependency = createIndex(dependencyIndex)
      .analysis(
        Analysis(
          analyzers = List(),
          normalizers = List(lowercase)
        )
      )
      .mapping(
        MappingDefinition(dependenciesFields)
      )

    Future
      .sequence(
        Seq(createProject, createRelease, createDependency)
          .map(request => esClient.execute(request))
      )
      .map { resps =>
        resps
          .filter(_.isError)
          .map(_.error)
          .foreach(error => logger.info(error.reason))
      }
  }

  def insertProject(project: Project): Future[Unit] = {
    esClient
      .execute(
        indexInto(projectIndex)
          .source(project)
      )
      .map(_ => ())
  }

  def updateProject(project: Project): Future[Unit] = {
    esClient
      .execute(
        updateById(projectIndex, project.id.get).doc(project)
      )
      .map(_ => ())
  }

  def insertReleases(releases: Seq[Release]): Future[Seq[BulkResponseItem]] = {
    val requests = releases.map { r =>
      indexInto(releaseIndex).source(ReleaseDocument(r))
    }
    insertAll(requests, 1000)
  }

  def insertRelease(release: Release): Future[Unit] = {
    esClient
      .execute {
        indexInto(releaseIndex)
          .source(ReleaseDocument(release))
      }
      .map(_ => ())
  }

  def insertDependencies(
      dependencies: Seq[ScalaDependency]
  ): Future[Seq[BulkResponseItem]] = {
    val requests = dependencies.map { d =>
      indexInto(dependencyIndex).source(DependencyDocument(d))
    }
    insertAll(requests, 1000)
  }

  private def insertAll(
      requests: Seq[IndexRequest],
      bulkSize: Int
  ): Future[Seq[BulkResponseItem]] = {
    if (requests.nonEmpty) {
      Future
        .sequence(
          requests
            .grouped(bulkSize)
            .toSeq
            .map { reqs =>
              esClient.execute(bulk(reqs)).map(_.result.items)
            }
        )
        .map(_.flatten)
    } else {
      Future.successful(Seq.empty)
    }
  }

  def getTotalProjects(queryString: String): Future[Long] = {
    val query = must(
      notDeprecatedQuery,
      searchQuery(queryString, contributingSearch = false)
    )
    val request = search(projectIndex).query(query).size(0)
    esClient.execute(request).map(_.result.totalHits)
  }

  def autocompleteProjects(params: SearchParams): Future[Seq[Project]] = {
    val request = search(projectIndex)
      .query(gitHubStarScoring(filteredSearchQuery(params)))
      .sortBy(sortQuery(params.sorting))
      .limit(5)

    esClient
      .execute(request)
      .map(_.result.to[Project])
  }

  def findProjects(params: SearchParams): Future[Page[Project]] = {
    def clamp(page: Int): Int = if (page <= 0) 1 else page

    val request = search(projectIndex)
      .query(gitHubStarScoring(filteredSearchQuery(params)))
      .sortBy(sortQuery(params.sorting))
      .from(params.total * (clamp(params.page) - 1))
      .size(params.total)

    esClient
      .execute(request)
      .map { response =>
        Page(
          Pagination(
            current = clamp(params.page),
            pageCount = Math
              .ceil(response.result.totalHits / params.total.toDouble)
              .toInt,
            itemCount = response.result.totalHits
          ),
          response.result.to[Project].map(_.formatForDisplaying)
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

    val request = search(releaseIndex).query(query).size(5000)

    esClient
      .execute(request)
      .map(
        _.result.to[ReleaseDocument].map(_.toRelease).filter(_.isValid)
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
    val query = nestedQuery(
      path = "maven",
      must(
        termQuery("maven.groupId", reference.groupId),
        termQuery("maven.artifactId", reference.artifactId),
        termQuery("maven.version", reference.version)
      )
    )

    val request = search(releaseIndex).query(query).limit(1)

    esClient
      .execute(request)
      .map(_.result.to[ReleaseDocument].headOption.map(_.toRelease))
  }

  def getProject(project: Project.Reference): Future[Option[Project]] = {
    val query = must(
      termQuery("organization.keyword", project.organization),
      termQuery("repository.keyword", project.repository)
    )

    val request = search(projectIndex).query(query).limit(1)

    esClient.execute(request).map(_.result.to[Project].headOption)
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
  def getAllDependencies(
      ref: Release.Reference
  ): Future[Seq[ScalaDependency]] = {
    val query = termQuery("dependentUrl", ref.httpUrl)

    val request =
      search(dependencyIndex).query(query).size(5000)

    esClient
      .execute(request)
      .map(_.result.to[DependencyDocument].map(_.toDependency))
  }

  /**
   * Get all the releases which depend on a the given release
   */
  def getReverseDependencies(
      ref: Release.Reference
  ): Future[Seq[ScalaDependency]] = {
    val query = termQuery("targetUrl", ref.httpUrl)

    val request = search(dependencyIndex)
      .query(query)
      .size(5000)

    esClient
      .execute(request)
      .map(_.result.to[DependencyDocument].map(_.toDependency))
  }

  def getLatestProjects(): Future[List[Project]] = {
    for {
      projects <- getLatest[Project](
        projectIndex,
        "created",
        frontPageCount
      )
    } yield projects.map(_.formatForDisplaying)
  }

  def getLatestReleases(): Future[List[Release]] = {
    getLatest[ReleaseDocument](releaseIndex, "released", frontPageCount)
      .map(_.map(_.toRelease))
  }

  def getMostDependentUpon(): Future[List[Project]] = {
    val request = search(projectIndex)
      .query(matchAllQuery())
      .limit(frontPageCount)
      .sortBy(sortQuery(Some("dependentCount")))
    esClient
      .execute(request)
      .map(_.result.to[Project].toList)
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
      .map(_.map { case (targetType, count) =>
        (targetType, labelizeTargetType(targetType), count)
      })
  }

  def getTargetTypes(
      params: SearchParams
  ): Future[List[(String, String, Long)]] = {
    stringAggregations("targetType", filteredSearchQuery(params))
      .map(addLabelsIfMissing(params.targetTypes.toSet))
      .map(_.map { case (targetType, count) =>
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
    versionAggregations("scalaJsVersion", notDeprecatedQuery, Js.isValid)
  }

  def getScalaJsVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations(
      "scalaJsVersion",
      filteredSearchQuery(params),
      Js.isValid
    )
      .map(addLabelsIfMissing(params.scalaJsVersions.toSet))
  }

  def getAllScalaNativeVersions(): Future[List[(String, Long)]] = {
    versionAggregations(
      "scalaNativeVersion",
      notDeprecatedQuery,
      Native.isValid
    )
  }

  def getScalaNativeVersions(
      params: SearchParams
  ): Future[List[(String, Long)]] = {
    versionAggregations(
      "scalaNativeVersion",
      filteredSearchQuery(params),
      Native.isValid
    ).map(addLabelsIfMissing(params.scalaNativeVersions.toSet))
  }

  def getAllSbtVersions(): Future[List[(String, Long)]] = {
    versionAggregations("sbtVersion", notDeprecatedQuery, Sbt.isValid)
  }

  def getSbtVersions(params: SearchParams): Future[List[(String, Long)]] = {
    versionAggregations(
      "sbtVersion",
      filteredSearchQuery(params),
      Sbt.isValid
    )
      .map(addLabelsIfMissing(params.sbtVersions.toSet))
  }

  def getTotalProjects(): Future[Long] = {
    esClient
      .execute(search(projectIndex))
      .map(_.result.totalHits)
  }

  def getTotalReleases(): Future[Long] = {
    esClient
      .execute(search(releaseIndex))
      .map(_.result.totalHits)
  }

  def getContributingProjects(): Future[List[Project]] = {
    val request = search(projectIndex)
      .query(
        functionScoreQuery(contributingQuery)
          .functions(randomScore(scala.util.Random.nextInt(10000)))
          .boostMode("sum")
      )
      .limit(frontPageCount)

    esClient
      .execute(request)
      .map(_.result.to[Project].toList)
  }

  private def getLatest[T: HitReader: Manifest](
      index: String,
      sortingField: String,
      size: Int
  ): Future[List[T]] = {
    val request = search(index)
      .query(notDeprecatedQuery)
      .sortBy(fieldSort(sortingField).desc())
      .limit(size)

    esClient.execute(request).map(r => r.result.to[T].toList)
  }

  private def stringAggregations(
      field: String,
      query: Query
  ): Future[List[(String, Long)]] = {
    aggregations(field, query).map(_.toList.sortBy(_._1).toList)
  }

  private def versionAggregations(
      field: String,
      query: Query,
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
      query: Query
  ): Future[Map[String, Long]] = {
    val aggregationName = s"${field}_count"

    val aggregation = termsAgg(aggregationName, field).size(50)

    val request = search(projectIndex)
      .query(query)
      .aggregations(aggregation)

    for (response <- esClient.execute(request)) yield {
      response.result.aggregations
        .result[Terms](aggregationName)
        .buckets
        .map { bucket =>
          bucket.key -> bucket.docCount
        }
        .toMap
    }
  }
}

object ESRepo extends LazyLogging with SearchProtocol {
  import ElasticDsl._

  private lazy val config =
    ConfigFactory.load().getConfig("org.scala_lang.index.data")
  private lazy val indexName = config.getString("index")

  def open()(implicit ec: ExecutionContext): ESRepo = {
    logger.info(s"Using elasticsearch index: $indexName")

    val props = ElasticProperties("http://localhost:9200")
    val esClient = ElasticClient(JavaClient(props))
    new ESRepo(esClient, indexName)
  }

  private def gitHubStarScoring(query: Query): Query = {
    val scorer = fieldFactorScore("github.stars")
      .missing(0)
      .modifier(FieldValueFactorFunctionModifier.LN2P)
    functionScoreQuery()
      .query(query)
      .functions(scorer)
      .boostMode(CombineFunction.Multiply)
  }

  private def filteredSearchQuery(params: SearchParams): Query = {
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

  private def sortQuery(sorting: Option[String]): Sort =
    sorting match {
      case Some("stars") =>
        fieldSort(
          "github.stars"
        ) missing "0" order SortOrder.DESC // mode MultiMode.Avg
      case Some("forks") =>
        fieldSort(
          "github.forks"
        ) missing "0" order SortOrder.DESC // mode MultiMode.Avg
      case Some("dependentCount") =>
        fieldSort(
          "dependentCount"
        ) missing "0" order SortOrder.DESC // mode MultiMode.Avg
      case Some("contributors") =>
        fieldSort(
          "github.contributorCount"
        ) missing "0" order SortOrder.DESC // mode MultiMode.Avg
      case Some("relevant") => scoreSort().order(SortOrder.Desc)
      case Some("created") => fieldSort("created").desc()
      case Some("updated") => fieldSort("updated").desc()
      case _ => scoreSort().order(SortOrder.Desc)
    }

  private val notDeprecatedQuery: Query = {
    not(termQuery("deprecated", true))
  }

  private def searchQuery(
      queryString: String,
      contributingSearch: Boolean
  ): Query = {
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

  private def topicsQuery(topics: Seq[String]): Query = {
    must(topics.map(topicQuery))
  }

  private def topicQuery(topic: String): Query = {
    termQuery("github.topics.keyword", topic)
  }

  private val cliQuery = termQuery("hasCli", true)

  private def repositoriesQuery(
      repositories: Seq[GithubRepo]
  ): Query = {
    should(repositories.map(repositoryQuery))
  }

  private def repositoryQuery(repo: GithubRepo): Query = {
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
  ): Query = {
    must(
      targetTypes.map(termQuery("targetType", _)) ++
        scalaVersions.map(termQuery("scalaVersion", _)) ++
        scalaJsVersions.map(termQuery("scalaJsVersion", _)) ++
        scalaNativeVersions.map(termQuery("scalaNativeVersion", _)) ++
        sbtVersions.map(termQuery("sbtVersion", _))
    )
  }

  private def targetQuery(target: ScalaTarget): Query = {
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
  private def luceneQuery(queryString: String): Query = {
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
    fieldMapping.foldLeft(queryString) { case (query, (input, replacement)) =>
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
    val missingLabels = labelSet -- result.map { case (label, _) =>
      label
    }.toSet

    (result ++ missingLabels.map(label => (label, 0L))).sortBy {
      case (label, _) => label
    }
  }

  private def optionalQuery(
      condition: Boolean,
      query: Query
  ): Query = {
    if (condition) query else matchAllQuery()
  }

  private def optionalQuery[P](
      param: Option[P]
  )(query: P => Query): Query = {
    param.map(query).getOrElse(matchAllQuery())
  }
}
