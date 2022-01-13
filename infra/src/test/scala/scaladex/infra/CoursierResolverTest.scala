package scaladex.infra

import java.nio.file.Paths
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.data.LocalPomRepository
import scaladex.core.util.ScalaExtensions._

class CoursierResolverTests extends AsyncFunSpec with Matchers {
  // need x + 1 threads where x is the number of concurrent downloads
  val fixedThreadPool: ExecutorService =
    Executors.newFixedThreadPool(11)
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(fixedThreadPool)

  it("should resolve parent pom from maven central") {
    val resolver = CoursierResolver(LocalPomRepository.MavenCentral)
    val pom = resolver.resolve("org.apache.parquet", "parquet", "1.8.2").get
    pom.getFileName shouldBe Paths.get("parquet-1.8.2.pom")
  }

  it("should resolve same pom 10 times concurrently") {
    val resolver = CoursierResolver(LocalPomRepository.MavenCentral)
    for {
      all <- Seq.fill(10)(Future(resolver.resolve("org.apache.parquet", "parquet", "1.12.2"))).sequence
    } yield {
      all.flatten.size shouldBe 10
      all.flatten.toSet.size shouldBe 1
    }
  }

  it("should resolve pom from bintray") {
    val resolver = CoursierResolver(LocalPomRepository.Bintray)
    val pom = resolver.resolve("com.twitter", "parquet", "1.6.0").get
    pom.getFileName() shouldBe Paths.get("parquet-1.6.0.pom")
  }

  it("should return None") {
    val resolver = CoursierResolver(LocalPomRepository.MavenCentral)
    val pom = resolver.resolve("ch.epfl.scala", "missing", "1.0.0")
    pom shouldBe None
  }
}
