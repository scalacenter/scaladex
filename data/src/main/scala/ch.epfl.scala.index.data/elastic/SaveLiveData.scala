package ch.epfl.scala.index
package data
package elastic

import model._
import project._
import maven.PomsReader

import com.sksamuel.elastic4s._
import ElasticDsl._

import org.json4s._
import org.json4s.native.Serialization.{read, writePretty, write}
import org.json4s.native.parseJson

import java.nio.file._
import java.nio.charset.StandardCharsets

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

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
  implicit val formats = DefaultFormats ++ Seq(LiveProjectsSerializer)
  implicit val serialization = native.Serialization
}

object SaveLiveData extends LiveProjectsProtocol {

  def storedProjects(paths: DataPaths) =
    read[LiveProjects](Files.readAllLines(paths.liveProjects).toArray.mkString("")).projects
}

class SaveLiveData(paths: DataPaths)(implicit val ec: ExecutionContext)
    extends LiveProjectsProtocol {

  def run(): Unit = {
    blockUntilYellow()

    val exists = Await.result(esClient.execute { indexExists(indexName) }, Duration.Inf).isExists()

    if (exists) {
      val projectConverter = new ProjectConvert(paths)
      val newData = projectConverter(
        PomsReader.loadAll(paths).collect { case Success(pomAndMeta) => pomAndMeta },
        stored = false
      )
      val (projects, projectReleases) = newData.unzip

      val limit = 5000

      val liveProjects =
        Await.result(esClient.execute {
          search.in(indexName / projectsCollection).query(termQuery("liveData", true)).size(limit)
        }.map(_.as[Project].toList), Duration.Inf)

      val liveProjectsForms =
        liveProjects.map(project => (project.reference, ProjectForm(project))).toMap

      val keepProjects = LiveProjects(SaveLiveData.storedProjects(paths) ++ liveProjectsForms)

      Files.write(paths.liveProjects, writePretty(keepProjects).getBytes(StandardCharsets.UTF_8))

      ()
    }
  }
}
