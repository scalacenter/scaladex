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
import scaladex.infra.elasticsearch.ElasticsearchEngine
import scaladex.infra.github.{GithubClient, GithubConfig}
import scaladex.infra.util.DoobieUtils
import scaladex.server.service.{GithubUpdater, SearchSynchronizer}
import scaladex.core.model.Platform
import scaladex.core.model.ScalaVersion
import scaladex.infra.storage.sql.SqlDatabase

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
        val searchSync = new SearchSynchronizer(database, searchEngine)

        // Will use GITHUB_TOKEN configured in github secret
        val githubConfig: GithubConfig = GithubConfig.load()
        val github = new GithubClient(githubConfig.token.get)
        val githubSync = new GithubUpdater(database, github)
        IO.fromFuture(IO {
          for {
            _ <- Init.run(config.dataPaths, database)
            _ <- searchEngine.reset()
            _ <- githubSync.run()
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

  // fails
  // missing:
  //   lift/framework
  //   playframework/play-json
  //   lihaoyi/upickle-pprint
  //   argonaut-io/argonaut
  test("top json") {
    val tops =
      List(
        "spray" -> "spray-json",
        "json4s" -> "json4s",
        "playframework" -> "play-json",
        "argonaut-io" -> "argonaut",
        "circe" -> "circe",
        "json4s" -> "json4s",
        "spray" -> "spray-json"
      )

    top("json", tops)
  }

  test("top database") {
    val tops =
      List(
        "slick" -> "slick",
        "tpolecat" -> "doobie",
        "getquill" -> "quill",
        "playframework" -> "anorm"
      )

    top("database", tops)
  }

  test("java targetTypes") {
    exactly(
      SearchParams(targetTypes = List("java")),
      List(("typesafehub", "config"))
    )
  }

  test("Scala.js targetTypes") {
    top(
      SearchParams(targetTypes = List("js")),
      List(
        ("scala-js", "scala-js")
      )
    )
  }

  test("Scala.js targetFiltering") {
    val scalaJs = Platform.ScalaJs(ScalaVersion.`2.12`, Platform.ScalaJs.`0.6`)

    top(
      SearchParams(targetFiltering = Some(scalaJs)),
      List(
        ("scala-js", "scala-js")
      )
    )
  }

  test("Scala.js targetFiltering (2)") {
    val scalaJs = Platform.ScalaJs(ScalaVersion.`2.12`, Platform.ScalaJs.`0.6`)

    compare(
      SearchParams(targetFiltering = Some(scalaJs)),
      List(),
      (_, obtained) => assert(obtained.nonEmpty)
    )
  }

  test("Scala Native targetTypes") {
    top(
      SearchParams(targetTypes = List("native")),
      List(
        ("scalaz", "scalaz"),
        ("scopt", "scopt"),
        ("scala-native", "scala-native"),
        ("scalaprops", "scalaprops")
      )
    )
  }

  test("Scala Native targetFiltering") {
    val scalaNative =
      Platform.ScalaNative(ScalaVersion.`2.11`, Platform.ScalaNative.`0.3`)

    top(
      SearchParams(targetFiltering = Some(scalaNative)),
      List(
        ("scalaz", "scalaz"),
        ("scopt", "scopt"),
        ("scala-native", "scala-native"),
        ("scalaprops", "scalaprops")
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
    val params = SearchParams(queryString = query, total = 12)
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
