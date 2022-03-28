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
import com.sksamuel.elastic4s.requests.script.Script
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.requests.searches.aggs.responses.bucket.Terms
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.queries.funcscorer.CombineFunction
import com.sksamuel.elastic4s.requests.searches.sort.Sort
import com.sksamuel.elastic4s.requests.searches.sort.SortOrder
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Category
import scaladex.core.model.GithubIssue
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.Stack
import scaladex.core.model.search.AwesomeParams
import scaladex.core.model.search.Page
import scaladex.core.model.search.PageParams
import scaladex.core.model.search.Pagination
import scaladex.core.model.search.ProjectDocument
import scaladex.core.model.search.ProjectHit
import scaladex.core.model.search.SearchParams
import scaladex.core.model.search.Sorting
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
  import ElasticsearchEngine._

  private val maxLanguagesOrPlatforms = 20

  private def waitUntilReady(): Future[Unit] = Future {
    var backoff = 0
    var done = false
    while (backoff <= 128 && !done) {
      if (backoff > 0) Thread.sleep(200L * backoff)
      backoff = backoff + 1

      val waitForYellowStatus =
        clusterHealth().waitForStatus(HealthStatus.Yellow)
      // TODO: rewrite without await
      val response = esClient.execute(waitForYellowStatus).await
      done = response.isSuccess
    }
    require(done, s"Failed waiting on: Expected cluster to have yellow status")
  }

  def close(): Unit = esClient.close()

  def init(reset: Boolean): Future[Unit] =
    for {
      _ <- waitUntilReady()
      indexExists <- esClient.execute(indexExists(index)).map(_.result.isExists)
      _ <-
        if (!indexExists) create()
        else if (reset)
          esClient.execute(deleteIndex(index)).flatMap(_ => create())
        else Future.unit
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

    logger.info(s"Creating index $index.")
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

  override def count(): Future[Int] = {
    val query = must(matchAllQuery())
    val request = search(index).query(query).size(0)
    esClient.execute(request).map(_.result.totalHits.toInt)
  }

  override def countByTopics(limit: Int): Future[Seq[(String, Int)]] =
    countAllUnique("githubInfo.topics.keyword", matchAllQuery(), limit)
      .map(_.sortBy(_._1))

  def countByLanguages(): Future[Seq[(Language, Int)]] =
    languageAggregation(matchAllQuery())

  def countByPlatforms(): Future[Seq[(Platform, Int)]] =
    platformAggregations(matchAllQuery())

  override def getMostDependedUpon(limit: Int): Future[Seq[ProjectDocument]] = {
    val request = search(index)
      .query(matchAllQuery())
      .sortBy(sortQuery(Sorting.Dependent))
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

  override def find(
      queryString: String,
      binaryVersion: Option[BinaryVersion],
      cli: Boolean,
      page: PageParams
  ): Future[Page[ProjectDocument]] = {
    val query = must(
      optionalQuery(cli, cliQuery),
      optionalQuery(binaryVersion)(binaryVersionQuery),
      searchQuery(queryString, false)
    )
    val request = search(index).query(gitHubStarScoring(query)).sortBy(sortQuery(Sorting.Relevance))
    findPage(request, page).map(_.flatMap(toProjectDocument))
  }
  override def find(params: SearchParams, page: PageParams): Future[Page[ProjectHit]] = {
    val request = search(index)
      .query(gitHubStarScoring(filteredSearchQuery(params)))
      .sortBy(sortQuery(params.sorting))
    findPage(request, page).map(_.flatMap(toProjectHit))
  }

  private def findPage(request: SearchRequest, page: PageParams): Future[Page[SearchHit]] = {
    val clamp = if (page.page <= 0) 1 else page.page
    val pagedRequest = request.from(page.size * (clamp - 1)).size(page.size)
    esClient
      .execute(pagedRequest)
      .map { response =>
        Page(
          Pagination(
            current = clamp,
            pageCount = Math
              .ceil(response.result.totalHits / page.size.toDouble)
              .toInt,
            totalSize = response.result.totalHits
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
          case Left(_) =>
            logger.warn("cannot parse beginner issue: ")
            None
        }
      }

  override def countByTopics(params: SearchParams, limit: Int): Future[Seq[(String, Int)]] =
    countAllUnique("githubInfo.topics.keyword", filteredSearchQuery(params), limit)
      .map(addMissing(params.topics))

  override def countByLanguages(params: SearchParams): Future[Seq[(Language, Int)]] =
    languageAggregation(filteredSearchQuery(params))
      .map(addMissing(params.languages.flatMap(Language.fromLabel)))

  override def countByPlatforms(params: SearchParams): Future[Seq[(Platform, Int)]] =
    platformAggregations(filteredSearchQuery(params))
      .map(addMissing(params.platforms.flatMap(Platform.fromLabel)))

  override def find(
      category: Category,
      params: AwesomeParams,
      page: PageParams
  ): Future[Page[ProjectDocument]] = {
    val query = awesomeQuery(category, params)
    val request = search(index)
      .query(gitHubStarScoring(query))
      .sortBy(sortQuery(params.sorting))

    findPage(request, page)
      .map(p => p.flatMap(toProjectDocument))
  }

  def countByLanguages(category: Category, params: AwesomeParams): Future[Seq[(Language, Int)]] =
    languageAggregation(awesomeQuery(category, params)).map(addMissing(params.languages))

  def countByPlatforms(category: Category, params: AwesomeParams): Future[Seq[(Platform, Int)]] =
    platformAggregations(awesomeQuery(category, params)).map(addMissing(params.platforms))

  private def languageAggregation(query: Query): Future[Seq[(Language, Int)]] =
    countAllUnique("languages", query, maxLanguagesOrPlatforms)
      .map { versionAgg =>
        for {
          (version, count) <- versionAgg.toList
          language <- Language.fromLabel(version)
        } yield (language, count)
      }
      .map(_.sortBy(_._1)(Language.ordering.reverse))

  private def platformAggregations(query: Query): Future[Seq[(Platform, Int)]] =
    countAllUnique("platforms", query, maxLanguagesOrPlatforms)
      .map { versionAgg =>
        for {
          (version, count) <- versionAgg.toList
          platform <- Platform.fromLabel(version)
        } yield (platform, count)
      }
      .map(_.sortBy(_._1)(Platform.ordering.reverse))

  private def countAllUnique(field: String, query: Query, limit: Int): Future[Seq[(String, Int)]] =
    aggregation(field, query, limit).map(_.buckets.map(b => b.key -> b.docCount.toInt))

  private def aggregation(field: String, query: Query, limit: Int): Future[Terms] = {
    val aggName = s"${field}_count"
    val aggregation = termsAgg(aggName, field).size(limit)

    val request = search(index).query(query).aggregations(aggregation)
    for (response <- esClient.execute(request))
      yield response.result.aggregations
        .result[Terms](aggName)
  }

  private def gitHubStarScoring(query: Query): Query = {
    val githubStarField = fieldAccess("githubInfo.stars", default = "1")
    val scalaPercentageField = fieldAccess("githubInfo.scalaPercentage", default = "100")
    val scorer = scriptScore(
      Script(
        script = s"Math.log($githubStarField * $scalaPercentageField + 1)"
      )
    )
    functionScoreQuery()
      .query(query)
      .functions(scorer)
      .boostMode(CombineFunction.Multiply)
  }

  private def awesomeQuery(category: Category, params: AwesomeParams): Query =
    must(
      termQuery("category", category.label),
      binaryVersionQuery(params.languages.map(_.label), params.platforms.map(_.label)),
      must(params.stacks.map(stackQuery))
    )

  private def filteredSearchQuery(params: SearchParams): Query =
    must(
      repositoriesQuery(params.userRepos.toSeq),
      topicsQuery(params.topics),
      binaryVersionQuery(params.languages, params.platforms),
      optionalQuery(params.contributingSearch, contributingQuery),
      searchQuery(params.queryString, params.contributingSearch)
    )

  private def sortQuery(sorting: Sorting): Sort =
    sorting match {
      case Sorting.Stars | Sorting.Forks =>
        fieldSort(s"githubInfo.${sorting.label}").missing("0").order(SortOrder.Desc)
      case Sorting.Contributors =>
        fieldSort(s"githubInfo.contributorCount").missing("0").order(SortOrder.Desc)
      case Sorting.Dependent =>
        fieldSort("inverseProjectDependencies").missing("0").order(SortOrder.Desc)
      case Sorting.Relevance => scoreSort().order(SortOrder.Desc)
      case Sorting.Created   => fieldSort("creationDate").desc()
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
          .field("formerReferences", 4)
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
              prefixQuery("formerReferences", prefix).boost(4),
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

  private def repositoriesQuery(repositories: Seq[Project.Reference]): Query =
    should(repositories.map(repositoryQuery))

  private def repositoryQuery(repo: Project.Reference): Query =
    must(
      termQuery("organization.keyword", repo.organization.value),
      termQuery("repository.keyword", repo.repository.value)
    )

  private def binaryVersionQuery(languages: Seq[String], platforms: Seq[String]): Query =
    must(languages.map(termQuery("languages", _)) ++ platforms.map(termQuery("platforms", _)))

  private def stackQuery(stack: Stack): Query =
    should(stack.projects.map(repositoryQuery) ++ stack.projects.map(projectDependencyQuery))

  private def projectDependencyQuery(dependency: Project.Reference): Query =
    termQuery("projectDependencies", dependency.toString())

  private def binaryVersionQuery(binaryVersion: BinaryVersion): Query =
    must(
      termQuery("platforms", binaryVersion.platform.label),
      termQuery("languages", binaryVersion.language.label)
    )

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

  private def addMissing[T: Ordering](required: Seq[T])(result: Seq[(T, Int)]): Seq[(T, Int)] = {
    val missingLabels = required.toSet -- result.map(_._1)
    val toAdd = missingLabels.map(label => (label, 0))
    (result ++ toAdd).sortBy(_._1)(implicitly[Ordering[T]].reverse)
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

  def fieldAccess(name: String): String =
    s"doc['$name'].value"

  def fieldAccess(name: String, default: String): String =
    s"(doc['$name'].size() != 0 ? doc['$name'].value : $default)"
}
