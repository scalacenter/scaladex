package scaladex.infra

import java.nio.file.Paths

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.util.ScalaExtensions.*

class CoursierResolverTests extends AsyncFunSpec with Matchers:
  val resolver = new CoursierResolver

  it("should resolve parent pom") {
    for pom <- resolver.resolve("org.apache.parquet", "parquet", "1.8.2")
    yield pom.get.getFileName shouldBe Paths.get("parquet-1.8.2.pom")
  }

  it("should resolve same pom 10 times concurrently") {
    for all <- Seq.fill(10)(resolver.resolve("org.apache.parquet", "parquet", "1.12.2")).sequence
    yield all.flatten.size shouldBe 10
  }
end CoursierResolverTests
