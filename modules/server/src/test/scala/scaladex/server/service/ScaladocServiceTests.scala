package scaladex.server.service

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.util.Using

import scaladex.core.model.Artifact
import scaladex.core.model.Version
import scaladex.core.service.MavenCentralClient

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ScaladocServiceTests extends AsyncFunSpec with Matchers:
  given ExecutionContext = ExecutionContext.global

  private val ref = Artifact.Reference.from("org.example", "lib_3", "1.0.0")
  private val missingRef = Artifact.Reference.from("org.example", "lib_3", "2.0.0")

  private def jar(files: (String, String)*): Array[Byte] =
    val out = ByteArrayOutputStream()
    Using.resource(ZipOutputStream(out)) { zip =>
      files.foreach { (name, content) =>
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.getBytes(StandardCharsets.UTF_8))
        zip.closeEntry()
      }
    }
    out.toByteArray
  end jar

  private class StubClient(jars: Map[Artifact.Reference, Array[Byte]]) extends MavenCentralClient:
    def getAllArtifactIds(groupId: Artifact.GroupId): Future[Seq[Artifact.ArtifactId]] = Future.successful(Seq.empty)
    def getAllVersions(groupId: Artifact.GroupId, artifactId: Artifact.ArtifactId): Future[Seq[Version]] =
      Future.successful(Seq.empty)
    def getPomFile(ref: Artifact.Reference): Future[(String, Instant)] = Future.failed(NotImplementedError())
    def getJavadocJar(ref: Artifact.Reference): Future[Option[Array[Byte]]] = Future.successful(jars.get(ref))

  private def filesIn(dir: Path): Seq[Path] =
    Using.resource(Files.walk(dir))(_.iterator.asScala.filter(Files.isRegularFile(_)).toSeq)

  private val docJar = jar("index.html" -> "root", "pkg/index.html" -> "pkg", "pkg/Foo.html" -> "foo")

  private def asString(file: Option[(String, Array[Byte])]): Option[(String, String)] =
    file.map((name, bytes) => name -> String(bytes, StandardCharsets.UTF_8))

  it("should serve files straight from the stored jar, without unpacking it") {
    val dir = Files.createTempDirectory("scaladoc-test")
    val service = ScaladocService(dir, StubClient(Map(ref -> docJar)), 1024 * 1024, 1024 * 1024)
    for
      available <- service.ensureAvailable(ref)
      root <- service.getFile(ref, "")
      file <- service.getFile(ref, "/pkg/Foo.html")
      pkgDir <- service.getFile(ref, "pkg")
      missing <- service.getFile(ref, "nope.html")
    yield
      available shouldBe true
      asString(root) shouldBe Some("index.html" -> "root")
      asString(file) shouldBe Some("pkg/Foo.html" -> "foo")
      asString(pkgDir) shouldBe Some("pkg/index.html" -> "pkg")
      missing shouldBe None
      filesIn(dir).map(_.getFileName.toString) shouldBe Seq("1.0.0.jar")
    end for
  }

  it("should report unavailable scaladoc") {
    val dir = Files.createTempDirectory("scaladoc-test")
    val service = ScaladocService(dir, StubClient(Map.empty), 1024 * 1024, 1024 * 1024)
    for
      available <- service.ensureAvailable(missingRef)
      file <- service.getFile(missingRef, "")
    yield
      available shouldBe false
      file shouldBe None
  }

  it("should reject jars over the size limit") {
    val dir = Files.createTempDirectory("scaladoc-test")
    val service = ScaladocService(dir, StubClient(Map(ref -> docJar)), 1024 * 1024, docJar.length - 1)
    service.ensureAvailable(ref).map { available =>
      available shouldBe false
      filesIn(dir) shouldBe empty
    }
  }

  it("should wipe jars left over by a previous run on startup") {
    val dir = Files.createTempDirectory("scaladoc-test")
    val leftover = dir.resolve("org/example/old_3/0.1.0.jar")
    Files.createDirectories(leftover.getParent)
    Files.write(leftover, docJar)
    ScaladocService(dir, StubClient(Map.empty), 1024 * 1024, 1024 * 1024)
    Files.exists(dir) shouldBe true
    filesIn(dir) shouldBe empty
  }
end ScaladocServiceTests
