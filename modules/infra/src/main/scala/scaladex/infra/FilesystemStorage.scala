package scaladex.infra

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Using

import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.syntax._
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.service.Storage
import scaladex.infra.Codecs._
import scaladex.infra.config.FilesystemConfig

class FilesystemStorage(projects: Path, temp: Path) extends Storage with LazyLogging {
  override def createTempFile(content: String, prefix: String, suffix: String): Path = {
    val tempFile = Files.createTempFile(temp, prefix, suffix)
    Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8))
  }

  override def deleteTempFile(path: Path): Unit = {
    assert(path.startsWith(temp))
    Files.delete(path)
  }

  override def clearProjects(): Unit =
    Using.resource(Files.walk(projects)) { stream =>
      stream.iterator.asScala
        .filter(!Files.isDirectory(_))
        .foreach(Files.delete)
    }

  override def saveProject(project: Project, artifacts: Seq[Artifact], dependencies: Seq[ArtifactDependency]): Unit = {
    val orgaDir = FilesystemStorage.initDir(projects, project.reference.organization.value)
    val repoDir = FilesystemStorage.initDir(orgaDir, project.reference.repository.value)

    val projectFile = FilesystemStorage.initFile(repoDir, "project.json")
    val artifactsFile = FilesystemStorage.initFile(repoDir, "artifacts.json")
    val dependenciesFile = FilesystemStorage.initFile(repoDir, "dependencies.json")

    writeJson(projectFile, project)
    writeJson(artifactsFile, artifacts)
    writeJson(dependenciesFile, dependencies)
  }

  override def loadAllProjects(): Iterator[(Project, Seq[Artifact], Seq[ArtifactDependency])] =
    for {
      orgaDir <- Files.list(projects).iterator.asScala
      if Files.isDirectory(orgaDir)
      repoDir <- Files.list(orgaDir).iterator.asScala
      if Files.isDirectory(repoDir)
      (project, artifacts, dependencies) <- tryLoadProject(repoDir)
    } yield (project, artifacts, dependencies)

  private def tryLoadProject(repoDir: Path): Option[(Project, Seq[Artifact], Seq[ArtifactDependency])] = {
    val projectFile = repoDir.resolve("project.json")
    val artifactsFile = repoDir.resolve("artifacts.json")
    val dependenciesFile = repoDir.resolve("dependencies.json")
    if (!Files.exists(projectFile) || !Files.exists(artifactsFile) || !Files.exists(dependenciesFile)) {
      logger.warn(s"Missing files for project in $repoDir")
      None
    } else {
      val tryParse = for {
        project <- readJson[Project](projectFile)
        artifacts <- readJson[Seq[Artifact]](artifactsFile)
        dependencies <- readJson[Seq[ArtifactDependency]](dependenciesFile)
      } yield (project, artifacts, dependencies)
      tryParse match {
        case Left(error) =>
          logger.warn(s"Failed to load project from $repoDir because of $error")
          None
        case Right(result) => Some(result)
      }
    }
  }

  private def writeJson[T: Encoder](file: Path, value: T): Unit = {
    val bytes = Printer.spaces2.print(value.asJson).getBytes(StandardCharsets.UTF_8)
    Files.write(file, bytes)
  }

  private def readJson[T: Decoder](file: Path): Either[Error, T] = {
    val content = Using.resource(Source.fromFile(file.toFile()))(_.mkString)
    parser.decode[T](content)
  }
}

object FilesystemStorage {
  def apply(config: FilesystemConfig): FilesystemStorage = {
    checkDir(config.index)
    checkDir(config.temp)
    val projects = initDir(config.index, "projects")
    new FilesystemStorage(projects, config.temp)
  }

  private def checkDir(directory: Path) =
    if (Files.notExists(directory)) Files.createDirectory(directory)
    else assert(Files.isDirectory(directory), s"$directory is not a directory")

  private def initDir(parent: Path, name: String): Path = {
    val directory = parent.resolve(name)
    checkDir(directory)
    directory
  }

  private def initFile(directory: Path, name: String): Path = {
    val file = directory.resolve(name)
    if (!Files.exists(file)) {
      Files.createFile(file)
    }
    file
  }
}
