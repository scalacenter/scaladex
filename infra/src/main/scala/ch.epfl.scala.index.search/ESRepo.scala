package ch.epfl.scala.index.search

import java.io.Closeable

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import ch.epfl.scala.index.model.misc._
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.search.Page
import ch.epfl.scala.search.Pagination
import ch.epfl.scala.search.ProjectDocument
import ch.epfl.scala.search.ProjectHit
import ch.epfl.scala.search.SearchParams
import ch.epfl.scala.services.SearchEngine
import ch.epfl.scala.utils.Codecs._
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl
import com.sksamuel.elastic4s.ElasticProperties
import com.sksamuel.elastic4s.analysis.Analysis
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.common.HealthStatus
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.aggs.responses.bucket.Terms
import com.sksamuel.elastic4s.requests.searches.queries.NoopQuery
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.queries.funcscorer.CombineFunction
import com.sksamuel.elastic4s.requests.searches.queries.funcscorer.FieldValueFactorFunctionModifier
import com.sksamuel.elastic4s.requests.searches.sort.Sort
import com.sksamuel.elastic4s.requests.searches.sort.SortOrder
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.circe._

/**
 * @param esClient TCP client of the elasticsearch server
 */
class ESRepo(esClient: ElasticClient, index: String)(implicit ec: ExecutionContext)
    extends SearchEngine
    with LazyLogging
    with Closeable {
  import ESRepo._
  import ElasticDsl._

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
    import DataMapping._
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

  override def insert(project: ProjectDocument): Future[String] = {
    val rawDocument = RawProjectDocument.from(project)
    val insertion = indexInto(index).withId(project.id).source(rawDocument)
    esClient.execute(insertion).map(response => response.result.id)
  }

  def refresh(): Future[Unit] =
    esClient
      .execute(refreshIndex(index))
      .map(_ => ())

  override def autocomplete(params: SearchParams): Future[Seq[ProjectDocument]] = {
    val request = search(index)
      .query(gitHubStarScoring(filteredSearchQuery(params)))
      .sortBy(sortQuery(params.sorting))
      .limit(5)

    esClient
      .execute(request)
      .map(response => response.result.hits.hits.toSeq.flatMap(toProjectDocument))
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

  private def toProjectDocument(hit: SearchHit): Option[ProjectDocument] =
    parser.decode[RawProjectDocument](hit.sourceAsString) match {
      case Right(rawDocument) =>
        Some(rawDocument.toProjectDocument)
      case Left(error) =>
        val source = hit.sourceAsMap
        val organization = source.get("organization").getOrElse("unknown")
        val repository = source.get("repository").getOrElse("unknown")
        logger.warn(s"cannot decode project document of $organization/$repository: ${error.getMessage}")
        None
    }

  private def toProjectHit(hit: SearchHit): Option[ProjectHit] = {
    val beginnerIssueHits = getBeginnerIssueHits(hit)
    toProjectDocument(hit).map(ProjectHit(_, beginnerIssueHits))
  }

  private def getBeginnerIssueHits(hit: SearchHit): Seq[GithubIssue] =
    hit.innerHits
      .get("beginnerIssues")
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

  override def getTopics(params: SearchParams): Future[Seq[(String, Long)]] =
    stringAggregations("githubInfo.topics.keyword", filteredSearchQuery(params))
      .map(addMissing(params.topics))

  override def getPlatformTypes(params: SearchParams): Future[Seq[(Platform.PlatformType, Long)]] =
    stringAggregations("platformTypes", filteredSearchQuery(params))
      .map(addMissing(params.targetTypes))
      .map(
        _.flatMap {
          case (platformType, count) =>
            Platform.PlatformType.ofName(platformType).map((_, count))
        }
      )

  override def getScalaVersions(params: SearchParams): Future[Seq[(String, Long)]] =
    aggregations("scalaVersions", filteredSearchQuery(params))
      .map(_.toSeq)
      .map(addMissing(params.scalaVersions))

  override def getScalaJsVersions(params: SearchParams): Future[Seq[(BinaryVersion, Long)]] =
    versionAggregations("scalaJsVersions", params, Platform.ScalaJs.isValid)
      .map(addMissing(params.scalaJsVersions.flatMap(BinaryVersion.parse)))

  override def getScalaNativeVersions(params: SearchParams): Future[Seq[(BinaryVersion, Long)]] =
    versionAggregations("scalaNativeVersions", params, Platform.ScalaNative.isValid)
      .map(addMissing(params.scalaNativeVersions.flatMap(BinaryVersion.parse)))

  override def getSbtVersions(params: SearchParams): Future[Seq[(BinaryVersion, Long)]] =
    versionAggregations("sbtVersions", params, Platform.SbtPlugin.isValid)
      .map(addMissing(params.sbtVersions.flatMap(BinaryVersion.parse)))

  private def stringAggregations(field: String, query: Query): Future[List[(String, Long)]] =
    aggregations(field, query).map(_.toList.sortBy(_._1).toList)

  private def versionAggregations(
      field: String,
      params: SearchParams,
      filterF: BinaryVersion => Boolean
  ): Future[Seq[(BinaryVersion, Long)]] = {
    val searchQuery = filteredSearchQuery(params)
    aggregations(field, searchQuery)
      .map { versionAgg =>
        for {
          (version, count) <- versionAgg.toList
          binaryVersion <- BinaryVersion.parse(version) if filterF(binaryVersion)
        } yield (binaryVersion, count)
      }
  }

  private def aggregations(field: String, query: Query): Future[Map[String, Long]] = {
    val aggregationName = s"${field}_count"

    val aggregation = termsAgg(aggregationName, field).size(50)

    val request = search(index)
      .query(query)
      .aggregations(aggregation)

    for (response <- esClient.execute(request))
      yield response.result.aggregations
        .result[Terms](aggregationName)
        .buckets
        .map(bucket => bucket.key -> bucket.docCount)
        .toMap
  }
}

object ESRepo extends LazyLogging {
  import ElasticDsl._

  private lazy val config = ConfigFactory.load().getConfig("elasticsearch")
  private lazy val indexName = config.getString("index")
  private lazy val port = config.getInt("port")

  def open()(implicit ec: ExecutionContext): ESRepo = {
    logger.info(s"Using elasticsearch index: $indexName")

    val props = ElasticProperties(s"http://localhost:$port")
    val esClient = ElasticClient(JavaClient(props))
    new ESRepo(esClient, indexName)
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

  private def sortQuery(sorting: Option[String]): Sort =
    sorting match {
      case Some(criteria @ ("stars" | "forks")) =>
        fieldSort(s"githubInfo.$criteria").missing("0").order(SortOrder.Desc)
      case Some("contributors") =>
        fieldSort(s"githubInfo.contributorCount").missing("0").order(SortOrder.Desc)
      case Some(criteria @ "dependent") =>
        fieldSort("inverseProjectDependencies").missing("0").order(SortOrder.Desc)
      case None | Some("relevant") => scoreSort().order(SortOrder.Desc)
      case Some("created")         => fieldSort("createdAt").desc()
      case Some("updated")         => fieldSort("updatedAt").desc()
      case Some(unknown) =>
        logger.warn(s"Unknown sort criteria: $unknown")
        scoreSort().order(SortOrder.Desc)
    }

  private val notDeprecatedQuery: Query =
    not(termQuery("deprecated", true))

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
          .field("githubInfo.description", 4)
          .field("githubInfo.topics", 4)
          .field("artifactNames", 2)

        val readmeMatch = matchQuery("githubInfo.readme", plainText).boost(0.5)

        val contributingQuery = {
          if (contributingSearch) {
            nestedQuery(
              "githubInfo.beginnerIssues",
              matchQuery("githubInfo.beginnerIssues.title", plainText)
            ).inner(innerHits("beginnerIssues").size(7))
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
    }

    must(filterQuery, plainTextQuery)
  }

  private def topicsQuery(topics: Seq[String]): Query =
    must(topics.map(topicQuery))

  private def topicQuery(topic: String): Query =
    termQuery("githubInfo.topics.keyword", topic)

  private val cliQuery = termQuery("hasCli", true)

  private def repositoriesQuery(
      repositories: Seq[GithubRepo]
  ): Query =
    should(repositories.map(repositoryQuery))

  private def repositoryQuery(repo: GithubRepo): Query =
    must(
      termQuery("organization.keyword", repo.organization),
      termQuery("repository.keyword", repo.repository)
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
        "githubInfo.beginnerIssues",
        existsQuery("githubInfo.beginnerIssues")
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

  private val frontPageCount = 12

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
