package scaladex.infra

import java.io.Closeable

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.ElasticProperties
import com.sksamuel.elastic4s.Response
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.common.HealthStatus
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.requests.searches.aggs.responses.bucket.Terms
import com.sksamuel.elastic4s.requests.searches.queries.NoopQuery
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.queries.funcscorer.CombineFunction
import com.sksamuel.elastic4s.requests.searches.queries.funcscorer.FieldValueFactorFunctionModifier
import com.sksamuel.elastic4s.requests.searches.sort.Sort
import com.sksamuel.elastic4s.requests.searches.sort.SortOrder
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Category
import scaladex.core.model.GithubIssue
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.search.Page
import scaladex.core.model.search.Pagination
import scaladex.core.model.search.ProjectDocument
import scaladex.core.model.search.ProjectHit
import scaladex.core.model.search.SearchParams
import scaladex.core.service.SearchEngine
import scaladex.infra.Codecs._
import scaladex.infra.config.ElasticsearchConfig
import scaladex.infra.elasticsearch.ElasticsearchMapping._
import scaladex.infra.elasticsearch.RawProjectDocument

/**
 * @param esClient TCP client of the elasticsearch server
 */
class ElasticsearchEngine(esClient: ElasticClient, index: String)(implicit ec: ExecutionContext)
    extends SearchEngine
    with LazyLogging
    with Closeable {

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

  def reset(): Future[Unit] =
    for {
      _ <- delete()
      _ = logger.info(s"Creating index $index.")
      _ <- create()
    } yield logger.info(s"Index $index created")

  private def delete(): Future[Unit] =
    for {
      response <- esClient.execute(indexExists(index))
      exist = response.result.isExists
      _ <-
        if (exist) esClient.execute(deleteIndex(index))
        else Future.successful(())
    } yield ()

  private def create(): Future[Unit] = {
    val createProject = createIndex(index)
      .analysis(
        Analysis(
          analyzers = List(englishReadme),
          charFilters = List(codeStrip, urlStrip),
          tokenFilters = List(englishStop, englishStemmer, englishPossessiveStemmer),
          normalizers = List(lowercase)
        )
      )
      .mapping(MappingDefinition(projectFields))

    esClient
      .execute(createProject)
      .map { resp =>
        if (resp.isError) logger.info(resp.error.reason)
        else ()
      }
  }

  override def insert(project: ProjectDocument): Future[Unit] = {
    val rawDocument = RawProjectDocument.from(project)
    val insertion = indexInto(index).withId(project.id).source(rawDocument)
    esClient.execute(insertion).map(_ => ())
  }

  override def delete(reference: Project.Reference): Future[Unit] = {
    val deletion = deleteById(index, reference.toString)
    esClient.execute(deletion).map(_ => ())
  }

  def refresh(): Future[Unit] =
    esClient.execute(refreshIndex(index)).map(_ => ())

  override def count(): Future[Long] = {
    val query = must(matchAllQuery())
    val request = search(index).query(query).size(0)
    esClient.execute(request).map(_.result.totalHits)
  }

  override def countByTopics(limit: Int): Future[Seq[(String, Long)]] =
    aggregations("githubInfo.topics.keyword", matchAllQuery(), limit)
      .map(_.sortBy(_._1))

  def countByPlatformTypes(limit: Int): Future[Seq[(Platform.PlatformType, Long)]] =
    aggregations("platformTypes", matchAllQuery(), limit)
      .map(
        _.flatMap {
          case (platformType, count) =>
            Platform.PlatformType.ofName(platformType).map((_, count))
        }
      )
      .map(_.sortBy(_._1))

  def countByScalaVersions(limit: Int): Future[Seq[(String, Long)]] =
    aggregations("scalaVersions", matchAllQuery(), limit)
      .map(_.sortBy(_._1))

  def countByScalaJsVersions(limit: Int): Future[Seq[(BinaryVersion, Long)]] =
    versionAggregations("scalaJsVersions", matchAllQuery(), Platform.ScalaJs.isValid, limit)

  def countByScalaNativeVersions(limit: Int): Future[Seq[(BinaryVersion, Long)]] =
    versionAggregations("scalaNativeVersions", matchAllQuery(), Platform.ScalaNative.isValid, limit)

  def countBySbtVersison(limit: Int): Future[Seq[(BinaryVersion, Long)]] =
    versionAggregations("sbtVersions", matchAllQuery(), Platform.SbtPlugin.isValid, limit)

  override def getMostDependedUpon(limit: Int): Future[Seq[ProjectDocument]] = {
    val request = search(index)
      .query(matchAllQuery())
      .sortBy(sortQuery(Some("dependent")))
      .limit(limit)
    esClient.execute(request).map(extractDocuments)
  }

  override def getLatest(limit: Int): Future[Seq[ProjectDocument]] = {
    val request = search(index)
      .query(matchAllQuery())
      .sortBy(fieldSort("creationDate").desc())
      .limit(limit)
    esClient.execute(request).map(extractDocuments)
  }

  override def autocomplete(params: SearchParams, limit: Int): Future[Seq[ProjectDocument]] = {
    val request = search(index)
      .query(gitHubStarScoring(filteredSearchQuery(params)))
      .sortBy(sortQuery(params.sorting))
      .limit(limit)
    esClient.execute(request).map(extractDocuments)
  }

  override def find(params: SearchParams): Future[Page[ProjectHit]] = {
    val request = search(index)
      .query(gitHubStarScoring(filteredSearchQuery(params)))
      .sortBy(sortQuery(params.sorting))
    findPage(request, params.page, params.total)
      .map(_.flatMap(toProjectHit))
  }

  private def findPage(request: SearchRequest, page: Int, total: Int): Future[Page[SearchHit]] = {
    val clamp = if (page <= 0) 1 else page
    val pagedRequest = request.from(total * (clamp - 1)).size(total)
    esClient
      .execute(pagedRequest)
      .map { response =>
        Page(
          Pagination(
            current = clamp,
            pageCount = Math
              .ceil(response.result.totalHits / total.toDouble)
              .toInt,
            itemCount = response.result.totalHits
          ),
          response.result.hits.hits.toSeq
        )
      }
  }

  private def extractDocuments(response: Response[SearchResponse]): Seq[ProjectDocument] =
    response.result.hits.hits.toSeq.flatMap(toProjectDocument)

  private def toProjectDocument(hit: SearchHit): Option[ProjectDocument] =
    parser.decode[RawProjectDocument](hit.sourceAsString) match {
      case Right(rawDocument) =>
        Some(rawDocument.toProjectDocument)
      case Left(error) =>
        val source = hit.sourceAsMap
        val organization = source.getOrElse("organization", "unknown")
        val repository = source.getOrElse("repository", "unknown")
        logger.warn(s"cannot decode project document of $organization/$repository: ${error.getMessage}")
        None
    }

  private def toProjectHit(hit: SearchHit): Option[ProjectHit] = {
    val openIssueHits = getOpenIssueHits(hit)
    toProjectDocument(hit).map(ProjectHit(_, openIssueHits))
  }

  private def getOpenIssueHits(hit: SearchHit): Seq[GithubIssue] =
    hit.innerHits
      .get("openIssues")
      .filter(_.total.value > 0)
      .toSeq
      .flatMap(_.hits)
      .flatMap { hit =>
        parser.decode[GithubIssue](hit.sourceAsString) match {
          case Right(issue) => Some(issue)
          case Left(error) =>
            logger.warn("cannot parse beginner issue: ")
            None
        }
      }

  override def countByTopics(params: SearchParams, limit: Int): Future[Seq[(String, Long)]] =
    aggregations("githubInfo.topics.keyword", filteredSearchQuery(params), limit)
      .map(addMissing(params.topics))

  override def countByPlatformTypes(params: SearchParams, limit: Int): Future[Seq[(Platform.PlatformType, Long)]] =
    aggregations("platformTypes", filteredSearchQuery(params), limit)
      .map(addMissing(params.targetTypes))
      .map(
        _.flatMap {
          case (platformType, count) =>
            Platform.PlatformType.ofName(platformType).map((_, count))
        }
      )
      .map(_.sortBy(_._1))

  override def countByScalaVersions(params: SearchParams, limit: Int): Future[Seq[(String, Long)]] =
    aggregations("scalaVersions", filteredSearchQuery(params), limit)
      .map(addMissing(params.scalaVersions))

  override def countByScalaJsVersions(params: SearchParams, limit: Int): Future[Seq[(BinaryVersion, Long)]] =
    versionAggregations("scalaJsVersions", filteredSearchQuery(params), Platform.ScalaJs.isValid, limit)
      .map(addMissing(params.scalaJsVersions.flatMap(BinaryVersion.parse)))

  override def countByScalaNativeVersions(params: SearchParams, limit: Int): Future[Seq[(BinaryVersion, Long)]] =
    versionAggregations("scalaNativeVersions", filteredSearchQuery(params), Platform.ScalaNative.isValid, limit)
      .map(addMissing(params.scalaNativeVersions.flatMap(BinaryVersion.parse)))

  override def countBySbtVersions(params: SearchParams, limit: Int): Future[Seq[(BinaryVersion, Long)]] =
    versionAggregations("sbtVersions", filteredSearchQuery(params), Platform.SbtPlugin.isValid, limit)
      .map(addMissing(params.sbtVersions.flatMap(BinaryVersion.parse)))

  override def getByCategory(category: Category, limit: Int): Future[Seq[ProjectDocument]] = {
    val query = must(termQuery("category.keyword", category.label))
    val request = search(index)
      .query(gitHubStarScoring(query))
      .sortBy(scoreSort().order(SortOrder.Desc))
      .size(limit)
    esClient
      .execute(request)
      .map(_.result.hits.hits.toSeq.flatMap(toProjectDocument))
  }

  private def versionAggregations(
      field: String,
      query: Query,
      predicate: BinaryVersion => Boolean,
      limit: Int
  ): Future[Seq[(BinaryVersion, Long)]] =
    aggregations(field, query, limit)
      .map { versionAgg =>
        for {
          (version, count) <- versionAgg.toList
          binaryVersion <- BinaryVersion.parse(version) if predicate(binaryVersion)
        } yield (binaryVersion, count)
      }
      .map(_.sortBy(_._1))

  private def aggregations(field: String, query: Query, limit: Int): Future[Seq[(String, Long)]] = {
    val aggregationName = s"${field}_count"

    val aggregation = termsAgg(aggregationName, field).size(limit)

    val request = search(index)
      .query(query)
      .aggregations(aggregation)

    for (response <- esClient.execute(request))
      yield response.result.aggregations
        .result[Terms](aggregationName)
        .buckets
        .map(bucket => bucket.key -> bucket.docCount)
  }

  private def gitHubStarScoring(query: Query): Query = {
    val scorer = fieldFactorScore("githubInfo.stars")
      .missing(0)
      .modifier(FieldValueFactorFunctionModifier.LN2P)
    functionScoreQuery()
      .query(query)
      .functions(scorer)
      .boostMode(CombineFunction.Multiply)
  }

  private def filteredSearchQuery(params: SearchParams): Query =
    must(
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

  private def sortQuery(sorting: Option[String]): Sort =
    sorting match {
      case Some(criteria @ ("stars" | "forks")) =>
        fieldSort(s"githubInfo.$criteria").missing("0").order(SortOrder.Desc)
      case Some("contributors") =>
        fieldSort(s"githubInfo.contributorCount").missing("0").order(SortOrder.Desc)
      case Some(criteria @ "dependent") =>
        fieldSort("inverseProjectDependencies").missing("0").order(SortOrder.Desc)
      case None | Some("relevant") => scoreSort().order(SortOrder.Desc)
      case Some("created")         => fieldSort("creationDate").desc()
      case Some("updated")         => fieldSort("updateDate").desc()
      case Some(unknown) =>
        logger.warn(s"Unknown sort criteria: $unknown")
        scoreSort().order(SortOrder.Desc)
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

    val plainTextQuery =
      if (plainText.isEmpty || plainText == "*") matchAllQuery()
      else {
        val multiMatch = multiMatchQuery(plainText)
          .field("repository", 6)
          .field("primaryTopic", 5)
          .field("organization", 5)
          .field("formerReferences.repository", 5)
          .field("formerReferences.organization", 4)
          .field("githubInfo.description", 4)
          .field("githubInfo.topics", 4)
          .field("artifactNames", 2)

        val readmeMatch = matchQuery("githubInfo.readme", plainText).boost(0.5)

        val contributingQuery =
          if (contributingSearch) {
            nestedQuery(
              "githubInfo.openIssues",
              matchQuery("githubInfo.openIssues.title", plainText)
            ).inner(innerHits("openIssues").size(7))
              .boost(4)
          } else matchNoneQuery()

        val autocompleteQuery = plainText
          .split(" ")
          .lastOption
          .map { prefix =>
            dismax(
              prefixQuery("repository", prefix).boost(6),
              prefixQuery("primaryTopic", prefix).boost(5),
              prefixQuery("organization", prefix).boost(5),
              prefixQuery("formerReferences.repository", prefix).boost(5),
              prefixQuery("formerReferences.organization", prefix).boost(4),
              prefixQuery("githubInfo.description", prefix).boost(4),
              prefixQuery("githubInfo.topics", prefix).boost(4),
              prefixQuery("artifactNames", prefix).boost(2)
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

    must(filterQuery, plainTextQuery)
  }

  private def topicsQuery(topics: Seq[String]): Query =
    must(topics.map(topicQuery))

  private def topicQuery(topic: String): Query =
    termQuery("githubInfo.topics.keyword", topic)

  private val cliQuery = termQuery("hasCli", true)

  private def repositoriesQuery(
      repositories: Seq[Project.Reference]
  ): Query =
    should(repositories.map(repositoryQuery))

  private def repositoryQuery(repo: Project.Reference): Query =
    must(
      termQuery("organization.keyword", repo.organization.value),
      termQuery("repository.keyword", repo.repository.value)
    )

  private def targetsQuery(
      targetTypes: Seq[String],
      scalaVersions: Seq[String],
      scalaJsVersions: Seq[String],
      scalaNativeVersions: Seq[String],
      sbtVersions: Seq[String]
  ): Query =
    must(
      targetTypes.map(termQuery("platformTypes", _)) ++
        scalaVersions.map(termQuery("scalaVersions", _)) ++
        scalaJsVersions.map(termQuery("scalaJsVersions", _)) ++
        scalaNativeVersions.map(termQuery("scalaNativeVersions", _)) ++
        sbtVersions.map(termQuery("sbtVersions", _))
    )

  private def targetQuery(target: Platform): Query =
    target match {
      case jvm: Platform.ScalaJvm =>
        termQuery("scalaVersions", jvm.scalaV.family)
      case Platform.ScalaJs(scalaVersion, jsVersion) =>
        must(
          termQuery("scalaVersions", scalaVersion.family),
          termQuery("scalaJsVersions", jsVersion.toString)
        )
      case Platform.ScalaNative(scalaVersion, nativeVersion) =>
        must(
          termQuery("scalaVersions", scalaVersion.family),
          termQuery("scalaNativeVersions", nativeVersion.toString)
        )
      case Platform.SbtPlugin(scalaVersion, sbtVersion) =>
        must(
          termQuery("scalaVersions", scalaVersion.family),
          termQuery("sbtVersions", sbtVersion.toString)
        )
      case Platform.Java =>
        must(NoopQuery) // not sure
    }

  private val contributingQuery = boolQuery().must(
    Seq(
      nestedQuery(
        "githubInfo.openIssues",
        existsQuery("githubInfo.openIssues")
      ),
      existsQuery("githubInfo.contributingGuide"),
      existsQuery("githubInfo.chatroom")
    )
  )

  /**
   * Treats the query inputted by a user as a lucene query
   *
   * @param queryString the query inputted by user
   * @return the elastic query definition
   */
  private def luceneQuery(queryString: String): Query =
    stringQuery(
      replaceFields(queryString)
    )

  private val fieldMapping = Map(
    // "depends-on" -> "dependencies", TODO fix or remove depends-on query
    "topics" -> "githubInfo.topics.keyword",
    "organization" -> "organization.keyword",
    "primaryTopic" -> "primaryTopic.keyword",
    "repository" -> "repository.keyword"
  )

  private def replaceFields(queryString: String) =
    fieldMapping.foldLeft(queryString) {
      case (query, (input, replacement)) =>
        val regex = s"(\\s|^)$input:".r
        regex.replaceAllIn(query, s"$$1$replacement:")
    }

  private def addMissing[T: Ordering](required: Seq[T])(result: Seq[(T, Long)]): Seq[(T, Long)] = {
    val missingLabels = required.toSet -- result.map(_._1)
    val toAdd = missingLabels.map(label => (label, 0L))
    (result ++ toAdd).sortBy(_._1)
  }

  private def optionalQuery(condition: Boolean, query: Query): Query =
    if (condition) query else matchAllQuery()

  private def optionalQuery[P](param: Option[P])(query: P => Query): Query =
    param.map(query).getOrElse(matchAllQuery())
}

object ElasticsearchEngine extends LazyLogging {
  def open(config: ElasticsearchConfig)(implicit ec: ExecutionContext): ElasticsearchEngine = {
    logger.info(s"Using elasticsearch index: ${config.index}")

    val props = ElasticProperties(s"http://localhost:${config.port}")
    val esClient = ElasticClient(JavaClient(props))
    new ElasticsearchEngine(esClient, config.index)
  }
}
