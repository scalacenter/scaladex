package scaladex.core.model

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class CategoryTests extends AnyFunSpec with Matchers {
  it("should compute category label and title") {
    Category.AlgorithmsAndDataStructures.label shouldBe "algorithms-and-data-structures"
    Category.AlgorithmsAndDataStructures.title shouldBe "Algorithms and Data Structures"
  }

  it("should compute meta-category label") {
    MetaCategory.AsynchronousConcurrentAndDistributedProgramming.label shouldBe "asynchronous-concurrent-and-distributed-programming"
  }
}
