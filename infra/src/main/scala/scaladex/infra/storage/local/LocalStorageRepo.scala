package scaladex.infra.storage.local

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import io.circe._
import io.circe.syntax._
import scaladex.core.model.Project
import scaladex.core.service.LocalStorageApi
import scaladex.infra.storage.DataPaths
import scaladex.infra.util.Codecs._

class LocalStorageRepo(dataPaths: DataPaths) extends LocalStorageApi {
  import LocalStorageRepo._
  private val singleThreadedContext = // TODO: Use a lock instead
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  override def saveDataForm(ref: Project.Reference, userData: Project.DataForm): Future[Unit] =
    Future {
      val stored = allDataForms()
      saveAllDataForms(stored + (ref -> userData))
    }(singleThreadedContext)

  override def allDataForms(): Map[Project.Reference, Project.DataForm] = {
    val fileContent = Files
      .readAllLines(dataPaths.liveProjects)
      .toArray
      .mkString("")
    parser.decode[Map[Project.Reference, Project.DataForm]](fileContent).toTry.get
  }

  override def saveAllDataForms(dataForms: Map[Project.Reference, Project.DataForm]): Unit = {
    val liveDir = dataPaths.liveProjects.getParent
    if (!Files.isDirectory(liveDir)) {
      Files.createDirectory(liveDir)
    }

    Files.write(
      dataPaths.liveProjects,
      Printer.noSpaces.print(dataForms.asJson).getBytes(StandardCharsets.UTF_8)
    )
  }
}

object LocalStorageRepo {
  implicit val decoder: Decoder[Map[Project.Reference, Project.DataForm]] =
    Decoder[Map[String, Json]].emap { map =>
      map.foldLeft[Either[String, Map[Project.Reference, Project.DataForm]]](Right(Map.empty)) {
        case (acc, (key, json)) =>
          for {
            res <- acc
            ref <- Try(Project.Reference.from(key)).toEither.left.map(_.getMessage)
            dataForm <- json.as[Project.DataForm].left.map(_.getMessage)
          } yield res + (ref -> dataForm)
      }
    }

  implicit val encoder: Encoder[Map[Project.Reference, Project.DataForm]] =
    Encoder[Map[String, Json]].contramap(map => map.map { case (key, value) => key.toString -> value.asJson })
}
