package scaladex.infra.storage.local

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import io.circe._
import io.circe.syntax._
import scaladex.core.model.Project
import scaladex.core.model.data.LocalPomRepository
import scaladex.core.service.LocalStorageApi
import scaladex.infra.storage.DataPaths
import scaladex.infra.util.Codecs._

class LocalStorageRepo(dataPaths: DataPaths, tempDir: Path) extends LocalStorageApi {
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
      .readAllLines(dataPaths.liveProjects)
      .toArray
      .mkString("")
    parser.decode[Map[Project.Reference, Project.Settings]](fileContent).toTry.get
  }

  override def saveAllProjectSettings(settings: Map[Project.Reference, Project.Settings]): Unit = {
    val liveDir = dataPaths.liveProjects.getParent
    if (!Files.isDirectory(liveDir)) {
      Files.createDirectory(liveDir)
    }

    Files.write(
      dataPaths.liveProjects,
      Printer.noSpaces.print(settings.asJson).getBytes(StandardCharsets.UTF_8)
    )
  }

  override def createTempFile(content: String, prefix: String, suffix: String): Path =
    Files.createTempFile(tempDir, prefix, suffix)

  override def deleteTempFile(path: Path): Unit = {
    assert(path.startsWith(tempDir))
    Files.delete(path)
  }

  override def savePom(data: String, sha1: String, repository: LocalPomRepository): Unit = {
    val destination = dataPaths.poms(repository).resolve(s"$sha1.pom")
    if (Files.exists(destination)) Files.delete(destination)
    Files.write(destination, data.getBytes(StandardCharsets.UTF_8))
  }
}

object LocalStorageRepo {
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
