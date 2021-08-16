package ch.epfl.scala.index.search

import ch.epfl.scala.index.model.misc.SearchParams
import org.scalatest._

import scala.concurrent.ExecutionContext
import ch.epfl.scala.index.model.release.ScalaJvm
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.index.model.release.MajorBinary
import org.scalatest.funsuite.AsyncFunSuite

class DataRepositoryTests extends AsyncFunSuite with BeforeAndAfterAll {
  implicit override val executionContext = ExecutionContext.Implicits.global

  private val data = ESRepo.open()
  data.waitUntilReady()

  test("match for cats with scala3") {
    data.findProjects(SearchParams(queryString = "cats")).map { page =>
      assert {
        page.items.headOption.toList
          .flatMap(_.scalaVersion)
          .contains("scala3")
      }
    }
  }

  test("search for cats_3") {
    val params = SearchParams(
      queryString = "cats",
      targetFiltering = Some(ScalaJvm(Scala3Version(MajorBinary(3))))
    )
    data.findProjects(params).map { page =>
      assert {
        page.items.headOption.toList
          .flatMap(_.scalaVersion)
          .contains("scala3")
      }
    }
  }

  override protected def afterAll(): Unit = data.close()

}
