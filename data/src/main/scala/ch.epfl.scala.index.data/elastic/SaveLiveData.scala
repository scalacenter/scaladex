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
object LiveProjects {
  implicit val formats       = DefaultFormats ++ Seq(LiveProjectsSerializer)
  implicit val serialization = native.Serialization
}
object LiveProjectsSerializer extends CustomSerializer[LiveProjects](format => (
  {
    case JObject(obj) => {
      implicit val formats = DefaultFormats
      LiveProjects(
        obj.map { case (k, v) => 
          val List(organization, repository) = k.split('/').toList

          (Project.Reference(organization, repository), v.extract[ProjectForm]) 
        }.toMap
      )
    }
  },
  {
    case l: LiveProjects =>
      JObject(
        l.projects
          .toList
          .sortBy{case (Project.Reference(organization, repository), _) => (organization, repository)}
          .map { case (Project.Reference(organization, repository), v) =>
            JField(s"$organization/$repository", parseJson(write(v)))
          }
      )
  }
))

object SaveLiveData {
  val releasesFile = liveIndexBase.resolve(Paths.get("releases.json"))
  val storedReleases = read[Set[Release]](Files.readAllLines(releasesFile).toArray.mkString(""))

  val projectsFile = liveIndexBase.resolve(Paths.get("projects.json"))
  val storedProjects = read[LiveProjects](Files.readAllLines(projectsFile).toArray.mkString("")).projects
}

class SaveLiveData(implicit val ec: ExecutionContext) {
  import SaveLiveData._

  def run(): Unit = {
    val exists = Await.result(esClient.execute { indexExists(indexName) }, Duration.Inf).isExists()

    val newData = ProjectConvert(
      PomsReader.load().collect { case Success(pomAndMeta) => pomAndMeta }
    )
    val (projects, projectReleases) = newData.unzip
    val releases                    = projectReleases.flatten

    val limit = 5000
    val liveReleases = 
      Await.result(esClient.execute {
        search
          .in(indexName / releasesCollection)
          .query(termQuery("liveData", true))
          .size(limit)
      }.map(_.as[Release].toSet), Duration.Inf)

    assert(liveReleases.size < limit, "too many new releases")

    val mavens = releases.map(_.maven).toSet
    val keepReleases = 
      (storedReleases ++ liveReleases)
        .filter(release => !mavens.contains(release.maven))
        .toList
        .sortBy(r => (r.maven.groupId, r.maven.artifactId, r.maven.version))
        .map(_.copy(liveData = false))

    Files.write(releasesFile, writePretty(keepReleases).getBytes(StandardCharsets.UTF_8))

    val liveProjects =
      Await.result(esClient.execute {
        search
          .in(indexName / projectsCollection)
          .query(termQuery("liveData", true))
          .size(limit)
      }.map(_.as[Project].toList), Duration.Inf)

    val liveProjectsForms = liveProjects.map(project => (project.reference, ProjectForm(project))).toMap

    val keepProjects = LiveProjects(storedProjects ++ liveProjectsForms)
      
    Files.write(
      liveIndexBase.resolve(Paths.get("projects.json")),
      writePretty(keepProjects).getBytes(StandardCharsets.UTF_8)
    )

    ()
  }
}