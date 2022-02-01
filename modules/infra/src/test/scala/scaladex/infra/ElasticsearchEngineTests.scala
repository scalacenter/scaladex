package scaladex.infra

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

import org.scalatest._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Jvm
import scaladex.core.model.Project
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative
import scaladex.core.model.search.SearchParams
import scaladex.infra.config.ElasticsearchConfig

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
      binaryVersion = Some(BinaryVersion(Jvm, Scala.`3`))
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

  test("count by languages") {
    val expected = Seq(Scala.`3` -> 1L, Scala.`2.13` -> 1L)
    val params = SearchParams(queryString = "cats")
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      languages <- searchEngine.countByLanguages(params, 3)
    } yield (languages should contain).theSameElementsInOrderAs(expected)
  }

  test("count by platforms") {
    val expected = Seq(
      Jvm -> 1L,
      ScalaJs.`1.x` -> 1L,
      ScalaJs.`0.6` -> 1L,
      ScalaNative.`0.4` -> 1L
    )
    val params = SearchParams(queryString = "cats")
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      scalaJsVersions <- searchEngine.countByPlatforms(params, 8)
    } yield (scalaJsVersions should contain).theSameElementsInOrderAs(expected)
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
