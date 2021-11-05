package ch.epfl.scala.services.storage.local

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import ch.epfl.scala.index.model.ProjectForm
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.services.LocalStorageApi
import ch.epfl.scala.services.storage.DataPaths
import org.json4s.CustomSerializer
import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.JField
import org.json4s.JObject
import org.json4s.Serialization
import org.json4s._
import org.json4s.native
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write
import org.json4s.native.Serialization.writePretty
import org.json4s.native.parseJson

class LocalStorageRepo(dataPaths: DataPaths) extends LocalStorageApi {
  import LocalStorageRepo._
  private val singleThreadedContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  override def saveDataForm(ref: NewProject.Reference, userData: ProjectForm): Future[Unit] =
    Future {
      val stored = storedProjects(dataPaths)
      val newProject = ref -> userData
      saveProjects(dataPaths, stored + newProject)
    }(singleThreadedContext)

}

object LocalStorageRepo {
  case class LiveProjects(projects: Map[NewProject.Reference, ProjectForm])
  object LiveProjectsSerializer
      extends CustomSerializer[LiveProjects](format =>
        (
          {
            case JObject(obj) =>
              implicit val formats = DefaultFormats
              LiveProjects(
                obj.map {
                  case (k, v) =>
                    val List(organization, repository) = k.split('/').toList

                    (
                      NewProject.Reference.from(organization, repository),
                      v.extract[ProjectForm]
                    )
                }.toMap
              )
          },
          {
            case l: LiveProjects =>
              JObject(
                l.projects.toList
                  .sortBy {
                    case (reference, _) =>
                      reference
                  }
                  .map {
                    case (NewProject.Reference(organization, repository), v) =>
                      JField(
                        s"$organization/$repository",
                        parseJson(write(v)(DefaultFormats))
                      )
                  }
              )
          }
        )
      )

  implicit val formats: Formats = DefaultFormats ++ Seq(LiveProjectsSerializer)
  implicit val serialization: Serialization = native.Serialization

  def parse(s: String): LiveProjects =
    read[LiveProjects](s)

  def saveProjects(dataPaths: DataPaths, live: Map[NewProject.Reference, ProjectForm]): Unit = {
    val projects = LiveProjects(live)

    val liveDir = dataPaths.liveProjects.getParent
    if (!Files.isDirectory(liveDir)) {
      Files.createDirectory(liveDir)
    }

    Files.write(
      dataPaths.liveProjects,
      writePretty(projects).getBytes(StandardCharsets.UTF_8)
    )
  }

  def storedProjects(dataPaths: DataPaths): Map[NewProject.Reference, ProjectForm] =
    parse(
      Files
        .readAllLines(dataPaths.liveProjects)
        .toArray
        .mkString("")
    ).projects
}
