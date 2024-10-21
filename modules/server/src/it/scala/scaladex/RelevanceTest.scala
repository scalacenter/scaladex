package scaladex

import scala.concurrent.Future
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.TestKit
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AsyncFunSuiteLike
import scaladex.data.init.Init
import scaladex.server.config.ServerConfig
import cats.effect.IO
import cats.effect.ContextShift

import scala.concurrent.ExecutionContext
import scaladex.core.model.search._
import scaladex.infra.{ElasticsearchEngine, FilesystemStorage, SqlDatabase}
import scaladex.infra.sql.DoobieUtils
import scaladex.server.service.SearchSynchronizer
import scaladex.server.service.DependencyUpdater
import scaladex.core.service.ProjectService
import scaladex.server.service.ArtifactService
import scaladex.core.model._

class RelevanceTest extends TestKit(ActorSystem("SbtActorTest")) with AsyncFunSuiteLike with BeforeAndAfterAll {

  import system.dispatcher

  private val config = ServerConfig.load()
  private val searchEngine = ElasticsearchEngine.open(config.elasticsearch)

  override def beforeAll(): Unit = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val datasource = DoobieUtils.getHikariDataSource(config.database)
    val transactor = DoobieUtils.transactor(datasource)
    transactor
      .use { xa =>
        val database = new SqlDatabase(datasource, xa)
        val filesystem = FilesystemStorage(config.filesystem)

        val projectService = new ProjectService(database, searchEngine)
        val searchSync = new SearchSynchronizer(database, projectService, searchEngine)
        val projectDependenciesUpdater = new DependencyUpdater(database, projectService)
        val artifactService = new ArtifactService(database)

        IO.fromFuture(IO {
          for {
            _ <- Init.run(database, filesystem)
            _ <- searchEngine.init(true)
            _ <- artifactService.updateAllLatestVersions().zip(projectDependenciesUpdater.updateAll())
            _ <- searchSync.syncAll()
            _ <- searchEngine.refresh()
          } yield ()
        })
      }
      .unsafeRunSync()
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    searchEngine.close()
  }

  // sometimes shows synsys/spark
  test("match for spark") {
    first("spark")("apache", "spark")
  }

  test("match for shapeless") {
    first("shapeless")("milessabin", "shapeless")
  }

  test("match for doobie") {
    first("doobie")("tpolecat", "doobie")
  }

  test("match for scalafix") {
    top("scalafix", List("scalacenter" -> "scalafix"))
  }

  test("top json") {
    top(
      "json",
      List(
        "spray" -> "spray-json",
        "json4s" -> "json4s",
        "playframework" -> "play-json",
        "argonaut-io" -> "argonaut",
        "circe" -> "circe",
        "json4s" -> "json4s",
        "spray" -> "spray-json",
        "com-lihaoyi" -> "upickle"
      )
    )
  }

  test("top database") {
    top(
      "database",
      List(
        "slick" -> "slick",
        "tpolecat" -> "doobie",
        "zio" -> "zio-quill",
        "playframework" -> "anorm"
      )
    )
  }

  test("Scala.js targetTypes") {
    top(
      SearchParams(platforms = Seq(ScalaJs.`1.x`)),
      List(
        "scala-js" -> "scala-js"
      )
    )
  }

  test("filter _sjs1_2.13") {
    top(
      SearchParams(languages = Seq(Scala.`2.13`), platforms = Seq(ScalaJs.`1.x`)),
      List(
        "scala-js" -> "scala-js"
      )
    )
  }

  test("filter Scala Native 0.4 platform") {
    top(
      SearchParams(platforms = List(ScalaNative.`0.4`)),
      List(
        ("scalaz", "scalaz"),
        ("scopt", "scopt"),
        ("scala-native", "scala-native")
      )
    )
  }

  test("filter _native0.4_2.13") {
    top(
      SearchParams(languages = Seq(Scala.`2.13`), platforms = Seq(ScalaNative.`0.4`)),
      List(
        ("scalaz", "scalaz"),
        ("scopt", "scopt"),
        ("scala-native", "scala-native")
      )
    )
  }

  test("most-depended upon") {
    for (mostDepended <- searchEngine.getMostDependedUpon(10)) yield {
      val mostDependedRefs = mostDepended.map(_.reference).toSet
      val expected = Seq(
        "scala/scala",
        "scalatest/scalatest",
        "scala-js/scala-js",
        "typelevel/scalacheck",
        "typelevel/cats"
      )
        .map(Project.Reference.unsafe)
      val missing = expected.filter(ref => !mostDependedRefs.contains(ref))
      assert(missing.isEmpty)
    }
  }

  private def first(query: String)(org: String, repo: String): Future[Assertion] = {
    val params = SearchParams(queryString = query)
    searchEngine.find(params, PageParams(1, 20)).map { page =>
      assert {
        page.items.headOption
          .map(_.document.reference)
          .contains(Project.Reference.from(org, repo))
      }
    }
  }

  private def top(params: SearchParams, tops: List[(String, String)]): Future[Assertion] =
    compare(
      params,
      tops,
      (expected, obtained) => {
        val missing = expected.toSet -- obtained.toSet
        assert(missing.isEmpty)
      }
    )

  private def top(query: String, tops: List[(String, String)]): Future[Assertion] = {
    val params = SearchParams(queryString = query)
    top(params, tops)
  }

  private def compare(
      params: SearchParams,
      expected: List[(String, String)],
      assertFun: (
          Seq[Project.Reference],
          Seq[Project.Reference]
      ) => Assertion
  ): Future[Assertion] = {
    val expectedRefs = expected.map {
      case (org, repo) =>
        Project.Reference.from(org, repo)
    }

    searchEngine.find(params, PageParams(1, 20)).map { page =>
      val obtainedRefs = page.items.map(_.document.reference)
      assertFun(expectedRefs, obtainedRefs)
    }
  }
}
