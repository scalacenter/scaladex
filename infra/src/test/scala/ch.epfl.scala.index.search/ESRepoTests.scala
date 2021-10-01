package ch.epfl.scala.index.search

import scala.concurrent.ExecutionContext

import ch.epfl.scala.index.Values
import ch.epfl.scala.index.model.misc.SearchParams
import ch.epfl.scala.index.model.release.MajorBinary
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Scala3Version
import org.scalatest._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class ESRepoTests extends AsyncFunSuite with Matchers with BeforeAndAfterAll {
  implicit override val executionContext: ExecutionContext =
    ExecutionContext.global

  private val data = ESRepo.open()

  override protected def beforeAll(): Unit = {
    data.waitUntilReady()
    data.deleteAll()
    data.create()
  }

  override protected def afterAll(): Unit = {
    data.deleteAll()
    data.close()
  }

  import Values._

  test("match for cats with scala3") {
    for {
      _ <- data.insertProject(Cats.projectDocument)
      _ <- data.refresh()
      page <- data.findProjects(SearchParams(queryString = "cats"))
    } yield {
      page.items.map(_.scalaVersion) should contain theSameElementsAs List(
        List("scala3")
      )
    }
  }

  test("search for cats_3") {
    val params = SearchParams(
      queryString = "cats",
      targetFiltering = Some(Platform.ScalaJvm(Scala3Version(MajorBinary(3))))
    )
    data.findProjects(params).map { page =>
      page.items.map(_.scalaVersion) should contain theSameElementsAs List(
        List("scala3")
      )
    }
  }

}
