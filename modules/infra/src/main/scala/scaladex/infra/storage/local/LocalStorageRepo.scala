package scaladex.infra.storage.local

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.Using

import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.syntax._
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.Project
import scaladex.core.model.data.LocalPomRepository
import scaladex.core.service.LocalStorageApi
import scaladex.infra.config.FilesystemConfig
import scaladex.infra.storage.DataPaths
import scaladex.infra.util.Codecs._

class LocalStorageRepo(dataPaths: DataPaths, projects: Path, projectSettings: Path, temp: Path)
    extends LocalStorageApi
    with LazyLogging {
  import LocalStorageRepo._
  private val singleThreadedContext = // TODO: Use a lock instead
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  override def saveProjectSettings(ref: Project.Reference, userData: Project.Settings): Future[Unit] =
    Future {
      val stored = getAllProjectSettings()
      saveAllProjectSettings(stored + (ref -> userData))
    }(singleThreadedContext)

  override def getAllProjectSettings(): Map[Project.Reference, Project.Settings] = {
    val fileContent = Files
      .readAllLines(projectSettings)
      .toArray
      .mkString("")
    parser.decode[Map[Project.Reference, Project.Settings]](fileContent).toTry.get
  }

  override def saveAllProjectSettings(settings: Map[Project.Reference, Project.Settings]): Unit =
    Files.write(
      projectSettings,
      Printer.noSpaces.print(settings.asJson).getBytes(StandardCharsets.UTF_8)
    )

  override def createTempFile(content: String, prefix: String, suffix: String): Path = {
    val tempFile = Files.createTempFile(temp, prefix, suffix)
    Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8))
  }

  override def deleteTempFile(path: Path): Unit = {
    assert(path.startsWith(temp))
    Files.delete(path)
  }

  override def savePom(data: String, sha1: String, repository: LocalPomRepository): Unit = {
    val destination = dataPaths.poms(repository).resolve(s"$sha1.pom")
    if (Files.exists(destination)) Files.delete(destination)
    Files.write(destination, data.getBytes(StandardCharsets.UTF_8))
  }

  def clearProjects(): Unit =
    Using.resource(Files.walk(projects))(_.iterator.asScala.foreach(Files.delete))

  def saveProject(project: Project, artifacts: Seq[Artifact], dependencies: Seq[ArtifactDependency]): Unit = {
    val orgaDir = LocalStorageRepo.initDir(projects, project.reference.organization.value)
    val repoDir = LocalStorageRepo.initDir(orgaDir, project.reference.repository.value)

    val projectFile = LocalStorageRepo.initFile(repoDir, "project.json")
    val artifactsFile = LocalStorageRepo.initFile(repoDir, "artifacts.json")
    val dependenciesFile = LocalStorageRepo.initFile(repoDir, "dependencies.json")

    writeJson(projectFile, project)
    writeJson(artifactsFile, artifacts)
    writeJson(dependenciesFile, dependencies)
  }

  def getAllProjects(): Iterator[(Project, Seq[Artifact], Seq[ArtifactDependency])] =
    for {
      orgaDir <- Files.list(projects).iterator.asScala
      if Files.isDirectory(orgaDir)
      repoDir <- Files.list(orgaDir).iterator.asScala
      if Files.isDirectory(repoDir)
      (project, artifacts, dependencies) <- tryGetProject(repoDir)
    } yield (project, artifacts, dependencies)

  private def tryGetProject(repoDir: Path): Option[(Project, Seq[Artifact], Seq[ArtifactDependency])] = {
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
    val content = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.mkString
    parser.decode[T](content)
  }
}

object LocalStorageRepo {
  def apply(dataPaths: DataPaths, config: FilesystemConfig): LocalStorageRepo = {
    checkDir(config.index)
    checkDir(config.temp)
    val liveDir = initDir(config.index, "live")
    val projectSettings = initJsonFile(liveDir, "projects.json")
    val projects = initDir(config.index, "projects")
    new LocalStorageRepo(dataPaths, projects, projectSettings, config.temp)
  }

  private def checkDir(directory: Path) =
    if (Files.notExists(directory)) Files.createDirectory(directory)
    else assert(Files.isDirectory(directory), s"$directory is not a directory")

  private def initDir(parent: Path, name: String): Path = {
    val directory = parent.resolve(name)
    checkDir(directory)
    directory
  }

  private def initJsonFile(directory: Path, name: String): Path = {
    val file = directory.resolve(name)
    if (!Files.exists(file)) {
      Files.createFile(file)
      Files.write(file, "{}".getBytes(StandardCharsets.UTF_8))
    }
    file
  }

  private def initFile(directory: Path, name: String): Path = {
    val file = directory.resolve(name)
    if (!Files.exists(file)) {
      Files.createFile(file)
    }
    file
  }

  implicit val decoder: Decoder[Map[Project.Reference, Project.Settings]] =
    Decoder[Map[String, Json]].emap { map =>
      map.foldLeft[Either[String, Map[Project.Reference, Project.Settings]]](Right(Map.empty)) {
        case (acc, (key, json)) =>
          for {
            res <- acc
            ref <- Try(Project.Reference.from(key)).toEither.left.map(_.getMessage)
            settings <- json.as[Project.Settings].left.map(_.getMessage)
          } yield res + (ref -> settings)
      }
    }

  implicit val encoder: Encoder[Map[Project.Reference, Project.Settings]] =
    Encoder[Map[String, Json]].contramap(map => map.map { case (key, value) => key.toString -> value.asJson })
}
