package scaladex.data
package maven

import java.nio.file.Files
import java.nio.file.Path

import scala.concurrent.Future

import scaladex.core.service.PomResolver

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PomsReaderTests extends AnyFunSpec with Matchers:
  // No parent resolution is needed for these self-contained POMs.
  private val noopResolver = new PomResolver:
    def resolve(groupId: String, artifactId: String, version: String): Future[Option[Path]] =
      Future.successful(None)

  private val reader = new PomsReader(noopResolver)

  private def readPom(content: String): scala.util.Try[ArtifactModel] =
    val file = Files.createTempFile("scaladex-pom", ".pom")
    Files.writeString(file, content)
    try reader.loadOne(file)
    finally Files.deleteIfExists(file)

  describe("PomsReader") {
    it("reads a POM that carries a forbidden distributionManagement.status") {
      val pom =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<project xmlns="http://maven.apache.org/POM/4.0.0">
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>org.scala-lang</groupId>
          |  <artifactId>scala-compiler</artifactId>
          |  <version>2.3.1</version>
          |  <distributionManagement>
          |    <status>deployed</status>
          |  </distributionManagement>
          |</project>
          |""".stripMargin

      val result = readPom(pom)
      result.isSuccess shouldBe true
      val model = result.get
      model.groupId shouldBe "org.scala-lang"
      model.artifactId shouldBe "scala-compiler"
      model.version shouldBe "2.3.1"
    }

    it("reads a POM whose distributionManagement uses the reserved 'local' repository id") {
      val pom =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<project xmlns="http://maven.apache.org/POM/4.0.0">
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>com.example</groupId>
          |  <artifactId>lib</artifactId>
          |  <version>1.0.0</version>
          |  <distributionManagement>
          |    <repository>
          |      <id>local</id>
          |      <url>file:///tmp/repo</url>
          |    </repository>
          |  </distributionManagement>
          |</project>
          |""".stripMargin

      val result = readPom(pom)
      result.isSuccess shouldBe true
      result.get.artifactId shouldBe "lib"
    }

    it("reads a regular POM without distributionManagement") {
      val pom =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<project xmlns="http://maven.apache.org/POM/4.0.0">
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>com.example</groupId>
          |  <artifactId>lib</artifactId>
          |  <version>1.0.0</version>
          |</project>
          |""".stripMargin

      val result = readPom(pom)
      result.isSuccess shouldBe true
      result.get.artifactId shouldBe "lib"
    }
  }
end PomsReaderTests
