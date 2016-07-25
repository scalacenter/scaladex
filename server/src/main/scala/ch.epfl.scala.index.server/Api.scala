package ch.epfl.scala.index
package server

import model._
import model.misc._
import release._
import misc.Pagination
import data.elastic._

import com.sksamuel.elastic4s._
import ElasticDsl._
import org.elasticsearch.search.sort.SortOrder

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

case class ProjectForm(
  // project
  contributorsWanted: Boolean = false,
  keywords: Set[String] = Set(),
  defaultArtifact: Option[String] = None,
  deprecated: Boolean = false,
  artifactDeprecations: Set[String] = Set(),

  // documentation
  customScalaDoc: Option[String] = None,
  documentationLinks: List[String] = List(),
  
  // apperance
  logoImageUrl: Option[String] = None,
  background: Option[String] = None,
  foregroundColor: Option[String] = None
)

class Api(github: Github)(implicit val ec: ExecutionContext) {
  private def hideId(p: Project) = p.copy(id = None)

  val resultsPerPage = 20

  val sortQuery = (sorting: Option[String]) =>
    sorting match {
      case Some("stars")    => fieldSort("github.stars") missing "0" order SortOrder.DESC mode MultiMode.Avg
      case Some("forks")    => fieldSort("github.forks") missing "0" order SortOrder.DESC mode MultiMode.Avg
      case Some("relevant") => scoreSort
      case Some("created")  => fieldSort("created") order SortOrder.DESC
      case Some("updated")  => fieldSort("updated") order SortOrder.DESC
      case _                => scoreSort
  }

  private def query(q: QueryDefinition,
                    page: PageIndex,
                    sorting: Option[String],
                    total: Int): Future[(Pagination, List[Project])] = {
    val clampedPage = if (page <= 0) 1 else page
    esClient.execute {
      search
        .in(indexName / projectsCollection)
        .query(q)
        .start(total * (clampedPage - 1))
        .limit(total)
        .sort(sortQuery(sorting))
    }.map(
        r =>
          (
              Pagination(
                  current = clampedPage,
                  totalPages = Math.ceil(r.totalHits / total.toDouble).toInt,
                  total = r.totalHits
              ),
              r.as[Project].toList.map(hideId)
        ))
  }

  def find(queryString: String,
           page: PageIndex,
           sorting: Option[String] = None,
           userRepos: Set[GithubRepo] = Set(),
           total: Int = resultsPerPage) = {
    val escaped = queryString.replaceAllLiterally("/", "\\/")
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
      else
        List(
            bool(
                should(
                    reposQueries: _*
                )
            ))

    query(
        bool(
            mustQueries = mustQueriesRepos,
            shouldQueries = List(
                termQuery("keywords", escaped),
                termQuery("github.description", escaped),
                termQuery("repository", escaped),
                termQuery("organization", escaped),
                termQuery("github.readme", escaped),
                stringQuery(escaped)
            ),
            notQueries = List()
        ),
        page,
        sorting,
        total
    )
  }

  def releases(project: Project.Reference, defaultArtifact: Option[String] = None): Future[List[Release]] = {

    val artifactReference = 
      defaultArtifact.map(artifact =>
        List(termQuery("reference.artifact", artifact))
      ).getOrElse(List())

    val references = List(
      termQuery("reference.organization", project.organization),
      termQuery("reference.repository", project.repository)
    ) ::: artifactReference

    esClient.execute {
      search
        .in(indexName / releasesCollection)
        .query(
            nestedQuery("reference").query(
                bool(
                    must(
                      references
                    )
                )
            )
        )
        .size(500)
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
    }.map(r => r.as[Project].headOption)
  }

  def projectPage(projectRef: Project.Reference,
                  selection: ReleaseSelection): Future[Option[(Project, ReleaseOptions)]] = {
    val projectAndReleases = for {
      project  <- project(projectRef)
      releases <- releases(projectRef, project.flatMap(_.defaultArtifact))
    } yield (project, releases)

    projectAndReleases.map {
      case (p, releases) =>
        p.flatMap(project => 
            DefaultRelease(project.repository, selection, releases, project.defaultArtifact)
              .map(sel => (project, sel.copy(artifacts = project.artifacts))))
    }
  }

  def updateProject(projectRef: Project.Reference, form: ProjectForm): Future[Boolean] = {
    import form._

    for {
      updatedProject <- project(projectRef).map(project =>
        project.map(_.copy(
          contributorsWanted = contributorsWanted,
          keywords = form.keywords,
          defaultArtifact = defaultArtifact,
          deprecated = deprecated,
          artifactDeprecations = artifactDeprecations,

          // documentation
          customScalaDoc = customScalaDoc,
          documentationLinks = documentationLinks.filterNot(_ == ""),
          
          // apperance
          logoImageUrl = logoImageUrl,
          background = background,
          foregroundColor = foregroundColor,

          liveData = true
        ))
      )
      ret <- updatedProject.flatMap( project =>
        project.id.map(id =>
          esClient.execute(update(id) in (indexName / projectsCollection) doc project).map(_ => true)
        )
      ).getOrElse(Future.successful(false))
    } yield ret
  }

  def latestProjects() =
    latest[Project](projectsCollection, "created", 12).map(_.map(hideId))
  def latestReleases() = latest[Release](releasesCollection, "released", 12)

  private def latest[T: HitAs: Manifest](collection: String, by: String, n: Int): Future[List[T]] = {
    esClient.execute {
      search.in(indexName / collection).query(matchAllQuery).sort(fieldSort(by) order SortOrder.DESC).limit(n)

    }.map(r => r.as[T].toList)
  }

  def keywords() = aggregations("keywords")
  def targets()  = aggregations("targets")
  def dependencies() = {
    // we remove testing or logging because they are always a dependency
    // we could have another view to compare testing frameworks
    val testOrLogging = Set(
        "akka/akka-slf4j",
        "akka/akka-testkit",
        "etorreborre/specs2",
        "etorreborre/specs2-core",
        "etorreborre/specs2-junit",
        "etorreborre/specs2-mock",
        "etorreborre/specs2-scalacheck",
        "lihaoyi/utest",
        "paulbutcher/scalamock-scalatest-support",
        "playframework/play-specs2",
        "playframework/play-test",
        "rickynils/scalacheck",
        "scala/scala-library",
        "scalatest/scalatest",
        "scalaz/scalaz-scalacheck-binding",
        "scopt/scopt",
        "scoverage/scalac-scoverage-plugin",
        "scoverage/scalac-scoverage-runtime",
        "spray/spray-testkit",
        "typesafehub/scala-logging",
        "typesafehub/scala-logging-slf4j"
    )

    aggregations("dependencies").map(agg =>
          agg.toList.sortBy(_._2)(Descending).filter {
        case (ref, _) =>
          !testOrLogging.contains(ref)
    })
  }

  /**
    * list all tags including number of facets
    * @param field the field name
    * @return
    */
  private def aggregations(field: String): Future[Map[String, Long]] = {

    import scala.collection.JavaConverters._
    import org.elasticsearch.search.aggregations.bucket.terms.StringTerms

    val aggregationName = s"${field}_count"

    esClient.execute {
      search
        .in(indexName / projectsCollection)
        .aggregations(
            aggregation.terms(aggregationName).field(field).size(50)
        )
    }.map(resp => {
      val agg = resp.aggregations.get[StringTerms](aggregationName)
      agg.getBuckets.asScala.toList.collect {
        case b: StringTerms.Bucket => b.getKeyAsString -> b.getDocCount
      }.toMap

    })
  }

  private def maxOption[T: Ordering](xs: List[T]): Option[T] =
    if (xs.isEmpty) None else Some(xs.max)
}
