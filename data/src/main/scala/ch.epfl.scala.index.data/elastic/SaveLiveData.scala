package ch.epfl.scala.index
package data
package elastic

import model._
import project._
import maven.PomsReader

import com.sksamuel.elastic4s._
import ElasticDsl._

import org.json4s.native.Serialization.writePretty

import java.nio.file._
import java.nio.charset.StandardCharsets

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

class SaveLiveData(implicit val ec: ExecutionContext) {

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
      }.map(_.as[Release].toList), Duration.Inf)

    assert(liveReleases.size < limit, "too many new releases")

    val mavens = releases.map(_.maven).toSet
    liveReleases.filter(release => !mavens.contains(release.maven))

    Files.write(
      liveIndexBase.resolve(Paths.get("releases.json")),
      writePretty(liveReleases).getBytes(StandardCharsets.UTF_8)
    )

    val liveProjects =
      Await.result(esClient.execute {
        search
          .in(indexName / projectsCollection)
          .query(termQuery("liveData", true))
          .size(limit)
      }.map(_.as[Project].toList), Duration.Inf)

    Files.write(
      liveIndexBase.resolve(Paths.get("projects.json")),
      writePretty(liveProjects.map(ProjectForm.apply)).getBytes(StandardCharsets.UTF_8)
    )

    ()
  }
}