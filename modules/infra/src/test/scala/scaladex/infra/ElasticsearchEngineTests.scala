package scaladex.infra

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.scalatest._
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Jvm
import scaladex.core.model.Project
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative
import scaladex.core.model.search.PageParams
import scaladex.core.model.search.ProjectDocument
import scaladex.core.model.search.SearchParams
import scaladex.core.model.search.Sorting
import scaladex.core.test.Values._
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.config.ElasticsearchConfig

class ElasticsearchEngineTests extends AsyncFreeSpec with Matchers with BeforeAndAfterAll {
  implicit override val executionContext: ExecutionContext =
    ExecutionContext.global

  val config: ElasticsearchConfig = ElasticsearchConfig.load()
  val searchEngine: ElasticsearchEngine = ElasticsearchEngine.open(config)
  val pageParams: PageParams = PageParams(1, 20)

  val projects: Seq[ProjectDocument] = Seq(Cats.projectDocument, Scalafix.projectDocument)

  private def insertAllProjects(): Future[Unit] =
    for {
      _ <- projects.map(searchEngine.insert).sequence
      _ <- searchEngine.refresh()
    } yield ()

  override protected def beforeAll(): Unit =
    Await.result(searchEngine.init(true), Duration.Inf)

  override protected def afterAll(): Unit =
    searchEngine.close()

  "match for cats with scala3" in {
    for {
      _ <- insertAllProjects()
      page <- searchEngine.find(SearchParams(queryString = "cats"), pageParams)
    } yield page.items.map(_.document) should contain theSameElementsAs List(Cats.projectDocument)
  }

  "sort by dependent, created, stars, forks, and contributors" in {
    val params = SearchParams(queryString = "*")
    val catsFirst = Seq(Cats.projectDocument, Scalafix.projectDocument)
    val scalafixFirst = Seq(Scalafix.projectDocument, Cats.projectDocument)
    for {
      _ <- insertAllProjects()
      byDependent <- searchEngine.find(params.copy(sorting = Sorting.Dependent), pageParams)
      byCreated <- searchEngine.find(params.copy(sorting = Sorting.Created), pageParams)
      byStars <- searchEngine.find(params.copy(sorting = Sorting.Stars), pageParams)
      byForks <- searchEngine.find(params.copy(sorting = Sorting.Forks), pageParams)
      byContributors <- searchEngine.find(params.copy(sorting = Sorting.Contributors), pageParams)
    } yield {
      byDependent.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
      byCreated.items.map(_.document) should contain theSameElementsInOrderAs scalafixFirst // todo fix
      byStars.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
      byForks.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
      byContributors.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
    }
  }

  "contributing search" in {
    val expected = Seq(Cats.issueAboutFoo)
    val params = SearchParams("foo", contributingSearch = true)
    for {
      _ <- insertAllProjects()
      hits <- searchEngine.find(params, pageParams)
    } yield hits.items.flatMap(_.beginnerIssueHits) should contain theSameElementsAs expected
  }

  "count by topics" in {
    val expected = Scalafix.githubInfo.topics.toSeq.sorted.map(_ -> 1L)
    for {
      _ <- insertAllProjects()
      topics <- searchEngine.countByTopics(10)
    } yield (topics should contain).theSameElementsInOrderAs(expected)
  }

  "count by languages" in {
    val expected = Seq(Scala.`3` -> 1L, Scala.`2.13` -> 1L)
    val params = SearchParams(queryString = "cats")
    for {
      _ <- insertAllProjects()
      languages <- searchEngine.countByLanguages(params)
    } yield (languages should contain).theSameElementsInOrderAs(expected)
  }

  "count by platforms" in {
    val expected = Seq(
      Jvm -> 1L,
      ScalaJs.`1.x` -> 1L,
      ScalaJs.`0.6` -> 1L,
      ScalaNative.`0.4` -> 1L
    )
    val params = SearchParams(queryString = "cats")
    for {
      _ <- insertAllProjects()
      scalaJsVersions <- searchEngine.countByPlatforms(params)
    } yield (scalaJsVersions should contain).theSameElementsInOrderAs(expected)
  }

  "remove missing document should not fail" in {
    for {
      _ <- searchEngine.delete(Cats.reference)
    } yield succeed
  }

  "should find project by former reference" in {
    val cats = Cats.projectDocument.copy(formerReferences = Seq(Project.Reference.from("kindlevel", "dogs")))
    for {
      _ <- searchEngine.insert(cats)
      _ <- searchEngine.refresh()
      byFormerOrga <- searchEngine.find(SearchParams("kindlevel"), pageParams)
      byFormerRepo <- searchEngine.find(SearchParams("dogs"), pageParams)
    } yield {
      byFormerOrga.items.map(_.document) should contain theSameElementsAs Seq(cats)
      byFormerRepo.items.map(_.document) should contain theSameElementsAs Seq(cats)
    }
  }

  "old search api" - {
    "search for 'cats'" in {
      for {
        _ <- insertAllProjects()
        page <- searchEngine.find("cats", None, false, pageParams)
      } yield page.items should contain only Cats.projectDocument
    }

    "search for Scala 3 projects" in {
      val binaryVersion = BinaryVersion(Jvm, Scala.`3`)
      for {
        _ <- insertAllProjects()
        page <- searchEngine.find("*", Some(binaryVersion), false, pageParams)
      } yield page.items should contain only Cats.projectDocument
    }
  }

}
