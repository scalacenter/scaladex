package ch.epfl.scala.index
package server

import model._
import release.{SemanticVersion, ScalaTarget, MavenReference}
import misc.Pagination

import data.elastic._
import data.cleanup.ArtifactNameParser

import com.sksamuel.elastic4s._
import ElasticDsl._
import org.elasticsearch.search.sort.SortOrder

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

class Api(github: Github)(implicit val ec: ExecutionContext) {
  private def hideId(p: Project) = p.copy(_id = None)

  val resultsPerPage: Int = 10

  val sortQuery = (sorting: Option[String]) =>
    sorting match {
      case Some("stars") => fieldSort("github.stars") missing "0" order SortOrder.DESC mode MultiMode.Avg
      case Some("forks") => fieldSort("github.forks") missing "0" order SortOrder.DESC mode MultiMode.Avg
      case Some("relevant") => scoreSort
      case Some("created") => fieldSort("created") order SortOrder.DESC
      case Some("updated") => fieldSort("updated") order SortOrder.DESC
      case _ => scoreSort
    }

  private def query(q: QueryDefinition, page: PageIndex, sorting: Option[String]): Future[(Pagination, List[Project])] = {
    val clampedPage = if(page <= 0) 1 else page
    esClient.execute {
      search
        .in(indexName / projectsCollection)
        .query(q)
        .start(resultsPerPage * (clampedPage - 1))
        .limit(resultsPerPage)
        .sort(sortQuery(sorting))
    }.map(r => (
      Pagination(
        current = clampedPage,
        totalPages = Math.ceil(r.totalHits / resultsPerPage.toDouble).toInt,
        total = r.totalHits
      ),
      r.as[Project].toList.map(hideId)
    ))
  }

  def find(queryString: String, page: PageIndex, sorting: Option[String] = None) =
    query(new QueryStringQueryDefinition(queryString.replaceAllLiterally("/", "\\/")), page, sorting)

  def organization(organization: String, page: PageIndex, sorting: Option[String] = None) =
    query(
      nestedQuery("reference").query(
        termQuery("reference.organization", organization)
      ),
      page,
      sorting
    )

  def releases(project: Project.Reference): Future[List[Release]] = {
    esClient.execute {
      search.in(indexName / releasesCollection).query(
        nestedQuery("reference").query(
          bool (
            must(
              termQuery("reference.organization", project.organization),
              termQuery("reference.repository", project.repository)
            )
          )
        )
      ).size(1000)
    }.map(_.as[Release].toList)
  }

  def project(project: Project.Reference): Future[Option[Project]] = {
    esClient.execute {
      search.in(indexName / projectsCollection).query(
        nestedQuery("reference").query(
          bool (
            must(
              termQuery("reference.organization", project.organization),
              termQuery("reference.repository", project.repository)
            )
          )
        )
      ).limit(1)
    }.map(r => r.as[Project].headOption)
  }
  def projectPage(projectRef: Project.Reference, selectedArtifact: Option[String] = None, selectedVersion: Option[SemanticVersion] = None):
    Future[Option[(Project, List[String], List[SemanticVersion], List[(ScalaTarget, MavenReference)], Release, Int)]] = {

    val projectAndReleases =
      for {
        project <- project(projectRef)
        releases <- releases(projectRef)
      } yield (project, releases)



    def finds[A, B](xs: List[(A, B)], fs: List[A => Boolean]): Option[(A, B)] = {
      fs match {
        case Nil => None
        case f :: h =>
          xs.find{ case (a, b) => f(a) } match {
            case None => finds(xs, h)
            case s    => s
          }
      }
    }

    def defaultRelease(project: Project, releases: List[Release]): Option[(Release, List[SemanticVersion], List[(ScalaTarget, MavenReference)])] = {
      val artifacts = releases.groupBy(_.reference.artifact).toList

      def specified(artifact: String): Boolean = Some(artifact) == selectedArtifact
      def projectDefault(artifact: String): Boolean = Some(artifact) == project.defaultArtifact
      def projectRepository(artifact: String): Boolean = project.reference.repository == artifact

      def alphabetically = artifacts.sortBy(_._1).headOption

      val artifact: Option[(String, List[Release], Option[ScalaTarget])] =
        selectedArtifact match {
          case Some(a) => {
            // artifact provided by the user
            // it can have a scala target or not
            ArtifactNameParser(a) match {
              case Some((name, target)) => {
                artifacts.find{ case (a, b) => a == name}.map{ case (a, b) => (a, b, Some(target))}
              }
              case None => finds(artifacts, List(specified _)).map{ case (a, b) => (a, b, None)}
            }  
          }
          case None => {
            // find artifact by heuristic
            (finds(artifacts, List(projectDefault _, projectRepository _)) match {
              case None => alphabetically
              case s => s
            }).map{ case (a, b) => (a, b, None)}
          }
        }

      artifact.flatMap{ case (_, releases, selectedTarget) =>

        val sortedByVersion = releases.sortBy(_.reference.version)(Descending)

        // version provided by the user
        val versionSelected =
          selectedVersion
            .map(version => sortedByVersion.filter(_.reference.version == version))
            .getOrElse(sortedByVersion)

        val latestStableTarget = {
          selectedTarget match {
            case None => {
              // select latest stable target if possible (ex: scala 2.11 over 2.12 and scala-js)
              val scalaJsOut =
                if(versionSelected.exists(_.reference.target.scalaJsVersion.isEmpty)) {
                  versionSelected.filter(_.reference.target.scalaJsVersion.isEmpty)
                } else versionSelected

              val preReleaseOut =
                if(scalaJsOut.exists(_.reference.target.scalaVersion.preRelease.isEmpty)) {
                  scalaJsOut.filter(_.reference.target.scalaVersion.preRelease.isEmpty)
                } else scalaJsOut

              preReleaseOut.sortBy(_.reference.target.scalaVersion)(Descending)
            }
            case Some(target) => 
              // target provided by the user
              versionSelected.filter(_.reference.target == target)
          }
        }

        val lastNonPreReleases = {
          // select last non preRelease release if possible (ex: 1.1.0 over 1.2.0-RC1)
          val nonPreRelease=
            if(latestStableTarget.exists(_.reference.version.preRelease.isEmpty)){
              latestStableTarget.filter(_.reference.version.preRelease.isEmpty)
            } else {
              latestStableTarget
            }

          val sorted = nonPreRelease.sortBy(_.reference.version)(Descending)

          // (ex: collect all 1.1.0 releases)
          sorted.headOption.map(v =>
            sorted.filter(_.reference.version == v.reference.version)
          ).getOrElse(Nil)
        }

        lastNonPreReleases.headOption.map(r =>
          (
            r,
            sortedByVersion.map(_.reference.version).distinct,
            lastNonPreReleases.map(r => (r.reference.target, r.maven))
          )
        )
      }
    }

    projectAndReleases.map{ case (p, releases) =>
      p.flatMap(project =>
        defaultRelease(project, releases).map{ case (release, versions, targets) =>
          val artifacts = releases.groupBy(_.reference.artifactReference).keys.toList.map(_.artifact)
          (project, artifacts, versions, targets, release, releases.size)
        }
      )
    }
  }

  def latestProjects() = latest[Project](projectsCollection, "created", 12).map(_.map(hideId))
  def latestReleases() = latest[Release](releasesCollection, "released", 12)

  private def latest[T: HitAs : Manifest](collection: String, by: String, n: Int): Future[List[T]] = {
    esClient.execute {
      search.in(indexName / collection)
        .query(matchAllQuery)
        .sort(fieldSort(by) order SortOrder.DESC)
        .limit(n)

    }.map(r => r.as[T].toList)
  }

  def keywords() = aggregations("keywords")
  def targets() = aggregations("targets")
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
      agg.toList.sortBy(_._2)(Descending).filter{ case (ref, _) =>
        !testOrLogging.contains(ref)
      }
    )
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
      search.in(indexName / projectsCollection).aggregations(
        aggregation.terms(aggregationName).field(field).size(50)
      )
    }.map( resp => {
      val agg = resp.aggregations.get[StringTerms](aggregationName)
      agg.getBuckets.asScala.toList.collect {
        case b: StringTerms.Bucket => b.getKeyAsString -> b.getDocCount
      }.toMap

    })
  }

  private def maxOption[T: Ordering](xs: List[T]): Option[T] = if(xs.isEmpty) None else Some(xs.max)
}
