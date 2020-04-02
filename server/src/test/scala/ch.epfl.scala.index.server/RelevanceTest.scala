package ch.epfl.scala.index
package server

import model.Project
import model.misc.SearchParams
import model.release.{ScalaJs, ScalaNative, ScalaTarget}
import model.SemanticVersion
import data.DataPaths
import data.elastic._
import data.github.GithubDownload
import org.scalatest._

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit

class RelevanceTest
    extends TestKit(ActorSystem("SbtActorTest"))
    with AsyncFunSuiteLike
    with BeforeAndAfterAll {

  import system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val paths = DataPaths(Nil)
  private val data = new DataRepository(paths, new GithubDownload(paths))

  blockUntilYellow()

  // sometimes shows synsys/spark
  test("match for spark") {
    first("spark")("apache", "spark")
    pending
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
  test("match for scalafmt") {
    first("scalafmt")("scalameta", "scalafmt")
    pending
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
        "lihaoyi" -> "upickle-pprint",
        "non" -> "jawn",
        "lift" -> "framework",
        "argonaut-io" -> "argonaut",
        "circe" -> "circe",
        "json4s" -> "json4s",
        "spray" -> "spray-json"
      )

    top("json", tops)
    pending
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
      SearchParams(targetTypes = List("Java")),
      List(("typesafehub", "config"))
    )
  }

  test("Scala.js targetTypes") {
    top(
      SearchParams(targetTypes = List("Js")),
      List(
        ("scala-js", "scala-js")
      )
    )
  }

  test("Scala.js targetFiltering") {
    val scalaJs =
      ScalaJs(
        scalaVersion = SemanticVersion(2, 12),
        scalaJsVersion = SemanticVersion(0, 6)
      )

    top(
      SearchParams(targetFiltering = Some(scalaJs)),
      List(
        ("scala-js", "scala-js")
      )
    )
  }

  test("Scala.js targetFiltering (2)") {
    val scalaJs =
      ScalaJs(
        scalaVersion = SemanticVersion(2, 12),
        scalaJsVersion = SemanticVersion(0, 6)
      )

    compare(
      SearchParams(targetFiltering = Some(scalaJs)),
      List(),
      (_, obtained) => assert(obtained.nonEmpty)
    )
  }

  test("Scala Native targetTypes") {
    top(
      SearchParams(targetTypes = List("Native")),
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
      ScalaNative(
        scalaVersion = SemanticVersion(2, 11),
        scalaNativeVersion = SemanticVersion(0, 3)
      )

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

  private def first(query: String)(org: String,
                                   repo: String): Future[Assertion] = {
    val params = SearchParams(queryString = query)
    data.findProjects(params).map { page =>
      assert {
        page.items.headOption
          .map(_.reference)
          .contains(Project.Reference(org, repo))
      }
    }
  }

  private def exactly(params: SearchParams,
                      tops: List[(String, String)]): Future[Assertion] = {
    compare(
      params,
      tops,
      (expected, obtained) => assert(expected == obtained)
    )
  }

  private def top(params: SearchParams,
                  tops: List[(String, String)]): Future[Assertion] = {
    compare(
      params,
      tops,
      (expected, obtained) => {
        val missing = expected.toSet -- obtained.toSet
        assert(missing.isEmpty)
      }
    )
  }

  private def top(query: String,
                  tops: List[(String, String)]): Future[Assertion] = {
    val params = SearchParams(queryString = query)
    top(params, tops)
  }

  private def compare(
      params: SearchParams,
      expected: List[(String, String)],
      assertFun: (Seq[Project.Reference], Seq[Project.Reference]) => Assertion
  ): Future[Assertion] = {

    val expectedRefs = expected.map {
      case (org, repo) =>
        Project.Reference(org, repo)
    }

    data.findProjects(params).map { page =>
      val obtainedRefs = page.items.map(_.reference)
      assertFun(expectedRefs, obtainedRefs)
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
    esClient.close()
  }
}
