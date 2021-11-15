package ch.epfl.scala.index.search

import scala.concurrent.ExecutionContext

import ch.epfl.scala.index.Values
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.search.SearchParams
import org.scalatest._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class ESRepoTests extends AsyncFunSuite with Matchers with BeforeAndAfterAll {
  implicit override val executionContext: ExecutionContext =
    ExecutionContext.global

  private val searchEngine = ESRepo.open()

  override protected def beforeAll(): Unit = {
    searchEngine.waitUntilReady()
    searchEngine.reset()
  }

  override protected def afterAll(): Unit =
    searchEngine.close()

  import Values._

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

}
