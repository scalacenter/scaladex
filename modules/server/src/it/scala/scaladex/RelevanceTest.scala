package scaladex

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AsyncFunSuiteLike
import scaladex.data.init.Init
import scaladex.server.config.ServerConfig
import cats.effect.IO
import cats.effect.ContextShift

import scala.concurrent.ExecutionContext
import scaladex.core.model.Project
import scaladex.core.model.search.SearchParams
import scaladex.infra.ElasticsearchEngine
import scaladex.infra.sql.DoobieUtils
import scaladex.server.service.SearchSynchronizer
import scaladex.core.model.Scala
import scaladex.infra.SqlDatabase
import scaladex.infra.DataPaths
import scaladex.infra.FilesystemStorage
import scaladex.core.model.BinaryVersion
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative
import scaladex.core.model.search.PageParams

class RelevanceTest extends TestKit(ActorSystem("SbtActorTest")) with AsyncFunSuiteLike with BeforeAndAfterAll {

  import system.dispatcher

  private val config = ServerConfig.load()
  private val searchEngine = ElasticsearchEngine.open(config.elasticsearch)

  override def beforeAll(): Unit = {
    searchEngine.waitUntilReady()

    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val transactor = DoobieUtils.transactor(config.database)
    transactor
      .use { xa =>
        val database = new SqlDatabase(config.database, xa)
        val dataPaths = DataPaths.from(config.filesystem)
        val filesystem = FilesystemStorage(config.filesystem)
        val searchSync = new SearchSynchronizer(database, searchEngine)

        IO.fromFuture(IO {
          for {
            _ <- Init.run(database, filesystem)
            _ <- searchEngine.reset()
            _ <- searchSync.run()
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
      SearchParams(platforms = List("sjs1")),
      List(
        "scala-js" -> "scala-js"
      )
    )
  }

  test("filter _sjs0.6_2.12") {
    val scalaJs = BinaryVersion(ScalaJs.`0.6`, Scala.`2.12`)

    top(
      SearchParams(binaryVersion = Some(scalaJs)),
      List(
        "scala-js" -> "scala-js"
      )
    )
  }

  test("filter _sjs0.6_2.12 (2)") {
    val scalaJs = BinaryVersion(ScalaJs.`0.6`, Scala.`2.12`)

    compare(
      SearchParams(binaryVersion = Some(scalaJs)),
      List(),
      (_, obtained) => assert(obtained.nonEmpty)
    )
  }

  test("filter Scala Native 0.4 platform") {
    top(
      SearchParams(platforms = List("native0.4")),
      List(
        ("scalaz", "scalaz"),
        ("scopt", "scopt"),
        ("scala-native", "scala-native")
      )
    )
  }

  test("filter _native0.3_2.11") {
    val scalaNative =
      BinaryVersion(ScalaNative.`0.3`, Scala.`2.11`)

    top(
      SearchParams(binaryVersion = Some(scalaNative)),
      List(
        ("scalaz", "scalaz"),
        ("scopt", "scopt"),
        ("scala-native", "scala-native")
      )
    )
  }

  private def first(query: String)(org: String, repo: String): Future[Assertion] = {
    val params = SearchParams(queryString = query)
    searchEngine.find(params).map { page =>
      assert {
        page.items.headOption
          .map(_.document.reference)
          .contains(Project.Reference.from(org, repo))
      }
    }
  }

  private def exactly(params: SearchParams, tops: List[(String, String)]): Future[Assertion] =
    compare(
      params,
      tops,
      (expected, obtained) => assert(expected == obtained)
    )

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
    val params = SearchParams(queryString = query, PageParams(1, 12))
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

    searchEngine.find(params).map { page =>
      val obtainedRefs = page.items.map(_.document.reference)
      assertFun(expectedRefs, obtainedRefs)
    }
  }
}
