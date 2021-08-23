package ch.epfl.scala.index
package data
package elastic

import java.nio.charset.StandardCharsets
import java.nio.file._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import ch.epfl.scala.index.data.project._
import ch.epfl.scala.index.model._
import org.json4s._
import org.json4s.native.Serialization.read
import org.json4s.native.Serialization.write
import org.json4s.native.Serialization.writePretty
import org.json4s.native.parseJson
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// this allows us to save project as json object sorted by keys
case class LiveProjects(projects: Map[Project.Reference, ProjectForm])
object LiveProjectsSerializer
    extends CustomSerializer[LiveProjects](format =>
      (
        {
          case JObject(obj) => {
            implicit val formats = DefaultFormats
            LiveProjects(
              obj.map { case (k, v) =>
                val List(organization, repository) = k.split('/').toList

                (
                  Project.Reference(organization, repository),
                  v.extract[ProjectForm]
                )
              }.toMap
            )
          }
        },
        { case l: LiveProjects =>
          JObject(
            l.projects.toList
              .sortBy { case (Project.Reference(organization, repository), _) =>
                (organization, repository)
              }
              .map { case (Project.Reference(organization, repository), v) =>
                import ch.epfl.scala.index.search.SearchProtocol._
                JField(s"$organization/$repository", parseJson(write(v)))
              }
          )
        }
      )
    )

trait LiveProjectsProtocol {
  implicit val formats: Formats = DefaultFormats ++ Seq(LiveProjectsSerializer)
  implicit val serialization: Serialization = native.Serialization
}

object SaveLiveData extends LiveProjectsProtocol {

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def storedProjects(paths: DataPaths): Map[Project.Reference, ProjectForm] =
    parse(
      Files
        .readAllLines(paths.liveProjects)
        .toArray
        .mkString("")
    ).projects

  def parse(s: String): LiveProjects =
    read[LiveProjects](s)

  def saveProjects(
      paths: DataPaths,
      live: Map[Project.Reference, ProjectForm]
  ): Unit = {
    val projects = LiveProjects(live)

    val liveDir = paths.liveProjects.getParent
    if (!Files.isDirectory(liveDir)) {
      Files.createDirectory(liveDir)
    }

    Files.write(
      paths.liveProjects,
      writePretty(projects).getBytes(StandardCharsets.UTF_8)
    )
  }

  // Note: we use a future here just to catch exceptions. Our code is blocking, though.
  def saveProject(project: Project, paths: DataPaths)(implicit
      ec: ExecutionContext
  ): Future[_] =
    Future {
      concurrent.blocking {
        val stored = SaveLiveData.storedProjects(paths)
        val newProject = (project.reference -> ProjectForm(project))

        logger.info(s"Writing projects at ${paths.liveProjects}")
        saveProjects(paths, stored + newProject)
      }
    }

}
