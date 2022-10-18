package scaladex.core.model

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class CategoryTests extends AnyFunSpec with Matchers {
  describe("category") {
    it("should compute category label and title") {
      Category.AlgorithmsAndDataStructures.label shouldBe "algorithms-and-data-structures"
      Category.AlgorithmsAndDataStructures.title shouldBe "Algorithms and Data Structures"
    }

    it("should all be owned by a single meta-category") {
      for (category <- Category.all)
        MetaCategory.all.filter(meta => meta.categories.contains(category)).size shouldBe 1
    }
  }

  describe("MetaCategory") {
    it("should compute meta-category label") {
      MetaCategory.AsynchronousAndConcurrentProgramming.label shouldBe "asynchronous-and-concurrent-programming"
    }

    it("categories should be sorted alphabetically") {
      for (meta <- MetaCategory.all) meta.categories shouldBe sorted
      for (meta <- MetaCategory.all) meta.seeAlsoCategories shouldBe sorted
    }
  }
}
