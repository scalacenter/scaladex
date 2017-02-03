package ch.epfl.scala.index
package data
package elastic

import model._
import project._
import org.json4s._
import org.json4s.native.Serialization.{read, write, writePretty}
import org.json4s.native.parseJson
import java.nio.file._
import java.nio.charset.StandardCharsets

import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

// this allows us to save project as json object sorted by keys
case class LiveProjects(projects: Map[Project.Reference, ProjectForm])
object LiveProjectsSerializer
    extends CustomSerializer[LiveProjects](
      format =>
        (
          {
            case JObject(obj) => {
              implicit val formats = DefaultFormats
              LiveProjects(
                obj.map {
                  case (k, v) =>
                    val List(organization, repository) = k.split('/').toList

                    (Project.Reference(organization, repository), v.extract[ProjectForm])
                }.toMap
              )
            }
          }, {
            case l: LiveProjects =>
              JObject(
                l.projects.toList.sortBy {
                  case (Project.Reference(organization, repository), _) =>
                    (organization, repository)
                }.map {
                  case (Project.Reference(organization, repository), v) =>
                    JField(s"$organization/$repository", parseJson(write(v)))
                }
              )
          }
      ))

trait LiveProjectsProtocol {
  implicit val formats: Formats = DefaultFormats ++ Seq(LiveProjectsSerializer)
  implicit val serialization: Serialization = native.Serialization
}

object SaveLiveData extends LiveProjectsProtocol {

  val logger = LoggerFactory.getLogger(this.getClass)

  def storedProjects(paths: DataPaths): Map[Project.Reference, ProjectForm] =
    read[LiveProjects](Files.readAllLines(paths.liveProjects).toArray.mkString("")).projects

  // Note: we use a future here just to catch exceptions. Our code is blocking, though.
  def saveProject(project: Project, paths: DataPaths)(implicit ec: ExecutionContext): Future[_] = Future {
    concurrent.blocking {
      val keepProjects =
        LiveProjects(
          SaveLiveData.storedProjects(paths) + (project.reference -> ProjectForm(project))
        )
      logger.debug(s"Writing projects at ${paths.liveProjects}")
      Files.write(paths.liveProjects, writePretty(keepProjects).getBytes(StandardCharsets.UTF_8))
    }
  }

}
