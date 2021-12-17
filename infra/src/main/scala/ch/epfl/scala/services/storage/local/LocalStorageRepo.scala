package ch.epfl.scala.services.storage.local

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.services.LocalStorageApi
import ch.epfl.scala.services.storage.DataPaths
import io.circe._
import io.circe.syntax._

class LocalStorageRepo(dataPaths: DataPaths) extends LocalStorageApi {
  import LocalStorageRepo._
  private val singleThreadedContext = // TODO: Use a lock instead
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  override def saveDataForm(ref: NewProject.Reference, userData: NewProject.DataForm): Future[Unit] =
    Future {
      val stored = allDataForms()
      saveAllDataForms(stored + (ref -> userData))
    }(singleThreadedContext)

  override def allDataForms(): Map[NewProject.Reference, NewProject.DataForm] = {
    val fileContent = Files
      .readAllLines(dataPaths.liveProjects)
      .toArray
      .mkString("")
    parser.decode[Map[NewProject.Reference, NewProject.DataForm]](fileContent).toTry.get
  }

  override def saveAllDataForms(dataForms: Map[NewProject.Reference, NewProject.DataForm]): Unit = {
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
  import ch.epfl.scala.utils.Codecs._
  implicit val decoder: Decoder[Map[NewProject.Reference, NewProject.DataForm]] =
    Decoder[Map[String, Json]].emap { map =>
      map.foldLeft[Either[String, Map[NewProject.Reference, NewProject.DataForm]]](Right(Map.empty)) {
        case (acc, (key, json)) =>
          for {
            res <- acc
            ref <- Try(NewProject.Reference.from(key)).toEither.left.map(_.getMessage)
            dataForm <- json.as[NewProject.DataForm].left.map(_.getMessage)
          } yield res + (ref -> dataForm)
      }
    }

  implicit val encoder: Encoder[Map[NewProject.Reference, NewProject.DataForm]] =
    Encoder[Map[String, Json]].contramap(map => map.map { case (key, value) => key.toString -> value.asJson })
}
