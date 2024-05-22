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
import scaladex.core.model.TopicCount
import scaladex.core.model.search.GithubInfoDocument
import scaladex.core.model.search.PageParams
import scaladex.core.model.search.ProjectDocument
import scaladex.core.model.search.SearchParams
import scaladex.core.model.search.Sorting
import scaladex.core.test.Values._
import scaladex.core.util.ScalaExtensions._
import scaladex.infra.config.ElasticsearchConfig

class ElasticsearchEngineTests extends AsyncFreeSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {
  implicit override val executionContext: ExecutionContext =
    ExecutionContext.global

  val config: ElasticsearchConfig = ElasticsearchConfig.load()
  val searchEngine: ElasticsearchEngine = ElasticsearchEngine.open(config)
  val pageParams: PageParams = PageParams(1, 20)

  val projects: Seq[ProjectDocument] = Seq(Cats.projectDocument, Scalafix.projectDocument)

  private def insertAll(projects: Seq[ProjectDocument]): Future[Unit] =
    for {
      _ <- projects.map(searchEngine.insert).sequence
      _ <- searchEngine.refresh()
    } yield ()

  override protected def beforeEach(): Unit =
    Await.result(searchEngine.init(true), Duration.Inf)

  override protected def afterAll(): Unit =
    searchEngine.close()

  "match for cats with scala3" in {
    for {
      _ <- insertAll(projects)
      page <- searchEngine.find(SearchParams(queryString = "cats"), pageParams)
    } yield page.items.map(_.document) should contain theSameElementsAs List(Cats.projectDocument)
  }

  "sort by dependent, created, stars, forks, and contributors" in {
    val params = SearchParams(queryString = "*")
    val catsFirst = Seq(Cats.projectDocument, Scalafix.projectDocument)
    val scalafixFirst = Seq(Scalafix.projectDocument, Cats.projectDocument)
    for {
      _ <- insertAll(projects)
      byDependent <- searchEngine.find(params.copy(sorting = Sorting.Dependent), pageParams)
      byCreated <- searchEngine.find(params.copy(sorting = Sorting.Created), pageParams)
      byStars <- searchEngine.find(params.copy(sorting = Sorting.Stars), pageParams)
      byCommitActivity <- searchEngine.find(params.copy(sorting = Sorting.CommitActivity), pageParams)
      byContributors <- searchEngine.find(params.copy(sorting = Sorting.Contributors), pageParams)
    } yield {
      (byDependent.items.map(_.document) should contain).theSameElementsInOrderAs(catsFirst)
      (byCreated.items.map(_.document) should contain).theSameElementsInOrderAs(scalafixFirst) // todo fix
      (byStars.items.map(_.document) should contain).theSameElementsInOrderAs(catsFirst)
      (byCommitActivity.items.map(_.document) should contain).theSameElementsInOrderAs(catsFirst)
      (byContributors.items.map(_.document) should contain).theSameElementsInOrderAs(catsFirst)
    }
  }

  "use percentage of Scala in scoring function" in {
    val params = SearchParams(queryString = "*")
    val p1 = projectDocument("org/p1", 800, 60)
    val p2 = projectDocument("org/p2", 1000, 50)
    val p3 = projectDocument("org/p3", 1500, 10)
    for {
      _ <- insertAll(Seq(p1, p2, p3))
      page <- searchEngine.find(params, pageParams)
    } yield (page.items.map(_.document) should contain).theSameElementsInOrderAs(Seq(p2, p1, p3))
  }

  "contributing search" in {
    val expected = Seq(Cats.issueAboutFoo)
    val params = SearchParams("foo", contributingSearch = true)
    for {
      _ <- insertAll(projects)
      hits <- searchEngine.find(params, pageParams)
    } yield hits.items.flatMap(_.issues) should contain theSameElementsAs expected
  }

  "count by topics" in {
    val expected = Scalafix.githubInfo.topics.toSeq.sorted.map(TopicCount(_, 1))
    for {
      _ <- insertAll(projects)
      topics <- searchEngine.countByTopics(10)
    } yield (topics should contain).theSameElementsInOrderAs(expected)
  }

  "count by languages" in {
    val expected = Seq(Scala.`3` -> 1L, Scala.`2.13` -> 1L)
    val params = SearchParams(queryString = "cats")
    for {
      _ <- insertAll(projects)
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
      _ <- insertAll(projects)
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
        _ <- insertAll(projects)
        page <- searchEngine.find("cats", None, false, pageParams)
      } yield page.items should contain only Cats.projectDocument
    }

    "search for Scala 3 projects" in {
      val binaryVersion = BinaryVersion(Jvm, Scala.`3`)
      for {
        _ <- insertAll(projects)
        page <- searchEngine.find("*", Some(binaryVersion), false, pageParams)
      } yield page.items should contain only Cats.projectDocument
    }
  }

  "should evaluate to field access syntax of the given field" in {
    val field = "githubInfo.stars"
    val accessExpr = ElasticsearchEngine.fieldAccess(field)
    accessExpr shouldBe "doc['githubInfo.stars'].value"
  }

  "should evaluate to a field access that checks for nullability, and provides a default value" in {
    val field = "githubInfo.stars"
    val accessExpr = ElasticsearchEngine.fieldAccess(field, default = "0")
    accessExpr shouldBe "(doc['githubInfo.stars'].size() != 0 ? doc['githubInfo.stars'].value : 0)"
  }

  private def projectDocument(ref: String, stars: Int, scalaPercentage: Int): ProjectDocument = {
    val githubInfo = GithubInfoDocument.empty.copy(stars = Some(stars), scalaPercentage = Some(scalaPercentage))
    ProjectDocument.default(Project.Reference.from(ref)).copy(githubInfo = Some(githubInfo))
  }

}
