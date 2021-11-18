package ch.epfl.scala.index

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.testkit.TestKit
import ch.epfl.scala.search.SearchParams
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.search.ESRepo
import org.scalatest.Assertion
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AsyncFunSuiteLike
import ch.epfl.scala.index.data.init.Init
import ch.epfl.scala.index.server.config.ServerConfig
import cats.effect.IO
import ch.epfl.scala.services.storage.sql.DatabaseConfig
import cats.effect.ContextShift
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.server.Github
import ch.epfl.scala.services.github.{GithubConfig, GithubImplementation}

import scala.concurrent.ExecutionContext
import ch.epfl.scala.services.storage.sql.SqlRepo
import ch.epfl.scala.utils.DoobieUtils
import com.typesafe.config.ConfigFactory
import scaladex.server.service.{GithubSynchronizer, SearchSynchronizer}

class RelevanceTest extends TestKit(ActorSystem("SbtActorTest")) with AsyncFunSuiteLike with BeforeAndAfterAll {

  import system.dispatcher

  private val config = ServerConfig.load()
  private val searchEngine = ESRepo.open()

  override def beforeAll(): Unit = {
    searchEngine.waitUntilReady()

    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    val transactor = DoobieUtils.transactor(config.dbConf)
    transactor
      .use { xa =>
        val db = new SqlRepo(config.dbConf, xa)
        val searchSync = new SearchSynchronizer(db, searchEngine)

        // Will user GITHUB_TOKEN configured in github secret
        val githubConfig: Option[GithubConfig] = GithubConfig.from(ConfigFactory.load())
        val github = new GithubImplementation(githubConfig.get)
        val githubSync = new GithubSynchronizer(db, github)
        IO.fromFuture(IO {
          for {
            _ <- Init.run(config.dataPaths, db)
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
  //   non/jawn
  //   lihaoyi/upickle-pprint
  //   argonaut-io/argonaut
  test("top json") {
    val tops =
      List(
        "spray" -> "spray-json",
        "json4s" -> "json4s",
        "playframework" -> "play-json",
        "non" -> "jawn",
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
          .contains(NewProject.Reference.from(org, repo))
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
          Seq[NewProject.Reference],
          Seq[NewProject.Reference]
      ) => Assertion
  ): Future[Assertion] = {

    val expectedRefs = expected.map {
      case (org, repo) =>
        NewProject.Reference.from(org, repo)
    }

    searchEngine.find(params).map { page =>
      val obtainedRefs = page.items.map(_.document.reference)
      assertFun(expectedRefs, obtainedRefs)
    }
  }
}
