package scaladex.infra.elasticsearch

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

import org.scalatest._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.Scala3Version
import scaladex.core.model.ScalaVersion
import scaladex.core.model.search.SearchParams
import scaladex.infra.elasticsearch.ElasticsearchEngine

class ElasticsearchEngineTests extends AsyncFunSuite with Matchers with BeforeAndAfterAll {
  implicit override val executionContext: ExecutionContext =
    ExecutionContext.global

  val config: ElasticsearchConfig = ElasticsearchConfig.load()
  val searchEngine: ElasticsearchEngine = ElasticsearchEngine.open(config)

  override protected def beforeAll(): Unit = {
    searchEngine.waitUntilReady()
    Await.result(searchEngine.reset(), Duration.Inf)
  }

  override protected def afterAll(): Unit =
    searchEngine.close()

  import scaladex.core.test.Values._

  test("match for cats with scala3") {
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      page <- searchEngine.find(SearchParams(queryString = "cats"))
    } yield page.items.map(_.document) should contain theSameElementsAs List(Cats.projectDocument)
  }

  test("search for cats_3") {
    val params = SearchParams(
      queryString = "cats",
      targetFiltering = Some(Platform.ScalaJvm(Scala3Version.`3`))
    )
    searchEngine.find(params).map { page =>
      page.items.map(_.document) should contain theSameElementsAs List(Cats.projectDocument)
    }
  }

  test("sort by dependent, created, stars, forks, and contributors") {
    val params = SearchParams(queryString = "*")
    val catsFirst = Seq(Cats.projectDocument, Scalafix.projectDocument)
    val scalafixFirst = Seq(Scalafix.projectDocument, Cats.projectDocument)
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.insert(Scalafix.projectDocument)
      _ <- searchEngine.refresh()
      byDependent <- searchEngine.find(params.copy(sorting = Some("dependent")))
      byCreated <- searchEngine.find(params.copy(sorting = Some("created")))
      byStars <- searchEngine.find(params.copy(sorting = Some("stars")))
      byForks <- searchEngine.find(params.copy(sorting = Some("forks")))
      byContributors <- searchEngine.find(params.copy(sorting = Some("contributors")))
    } yield {
      byDependent.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
      byCreated.items.map(_.document) should contain theSameElementsInOrderAs scalafixFirst // todo fix
      byStars.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
      byForks.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
      byContributors.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
    }
  }

  test("contributing search") {
    val expected = Seq(Cats.issueAboutFoo)
    val params = SearchParams("foo", contributingSearch = true)
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      hits <- searchEngine.find(params)
    } yield hits.items.flatMap(_.beginnerIssueHits) should contain theSameElementsAs expected
  }

  test("count by topics") {
    val expected = Scalafix.githubInfo.topics.toSeq.sorted.map(_ -> 1L)
    for {
      _ <- searchEngine.insert(Scalafix.projectDocument)
      _ <- searchEngine.refresh()
      topics <- searchEngine.countByTopics(10)
    } yield (topics should contain).theSameElementsInOrderAs(expected)
  }

  test("count by platform types") {
    val expected =
      Seq(Platform.PlatformType.Native -> 1L, Platform.PlatformType.Js -> 1L, Platform.PlatformType.Jvm -> 2L)
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      platformTypes <- searchEngine.countByPlatformTypes(10)
    } yield (platformTypes should contain).theSameElementsInOrderAs(expected)
  }

  test("count by Scala versions") {
    val expected = Seq(ScalaVersion.`2.13`.family -> 1L, Scala3Version.`3`.family -> 1L)
    val params = SearchParams(queryString = "cats")
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      scalaVersions <- searchEngine.countByScalaVersions(params, 3)
    } yield (scalaVersions should contain).theSameElementsInOrderAs(expected)
  }

  test("count by Scala.js versions") {
    val expected = Seq(Platform.ScalaJs.`0.6` -> 1L, Platform.ScalaJs.`1.x` -> 1L)
    val params = SearchParams(queryString = "cats")
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      scalaJsVersions <- searchEngine.countByScalaJsVersions(params, 3)
    } yield (scalaJsVersions should contain).theSameElementsInOrderAs(expected)
  }

  test("count by Scala Native versions") {
    val expected = Seq(Platform.ScalaNative.`0.4` -> 1L)
    val params = SearchParams(queryString = "cats")
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      scalaNativeVersions <- searchEngine.countByScalaNativeVersions(params, 3)
    } yield (scalaNativeVersions should contain).theSameElementsInOrderAs(expected)
  }

  test("remove missing document should not fail") {
    for {
      _ <- searchEngine.delete(Cats.reference)
    } yield succeed
  }

  test("should find project by former reference") {
    val cats = Cats.projectDocument.copy(formerReferences = Seq(Project.Reference.from("kindlevel", "dogs")))
    for {
      _ <- searchEngine.insert(cats)
      _ <- searchEngine.refresh()
      byFormerOrga <- searchEngine.find(SearchParams("kindlevel"))
      byFormerRepo <- searchEngine.find(SearchParams("dogs"))
    } yield {
      byFormerOrga.items.map(_.document) should contain theSameElementsAs Seq(cats)
      byFormerRepo.items.map(_.document) should contain theSameElementsAs Seq(cats)
    }
  }
}
