package ch.epfl.scala.index
package server

import api._
import model._

import data.elastic._
import com.sksamuel.elastic4s._
import ElasticDsl._

import upickle.default.{Reader, Writer, write => uwrite, read => uread}

import scala.concurrent.{Future, ExecutionContext}
import scala.language.reflectiveCalls

object AutowireServer extends autowire.Server[String, Reader, Writer]{
  def read[Result: Reader](p: String)  = uread[Result](p)
  def write[Result: Writer](r: Result) = uwrite(r)
}

object ApiImplementation {
  def setup = esClient.execute { indexExists(indexName) }
}

class ApiImplementation(github: Github, userState: Option[UserState])(implicit val ec: ExecutionContext) extends Api {
  private def hideId(p: Project) = p.copy(_id = None)

  def userInfo(): Option[UserInfo] = userState.map(_.user)
  def find(q: String, page: PageIndex): Future[(Pagination, List[Project])] = {
    val perPage = 25
    esClient.execute {
      search
        .in(indexName / collectionName)
        .query(s"*$q*")
        .start(perPage * page)
        .limit(perPage)
    }.map(r => (
      Pagination(
        current = page,
        total = Math.ceil(r.totalHits / perPage.toDouble).toInt
      ),
      r.as[Project].toList.map(hideId)
    ))
  }

  def projectPage(artifact: Artifact.Reference): Future[Option[Project]] = {
    val Artifact.Reference(organization, artifactName) = artifact
    esClient.execute {
      search.in(indexName / collectionName).query(
        nestedQuery("artifacts.reference").query(
          bool (
            must(
              termQuery("artifacts.reference.organization", organization),
              termQuery("artifacts.reference.name", artifactName)
            )
          )
        )
      ).limit(1)
    }.map(r => r.as[Project].headOption.map(hideId))
  }

  def latest(artifact: Artifact.Reference): Future[Option[Release.Reference]] = {
    projectPage(artifact).map(_.flatMap(
      _.artifacts
        .find(_.reference == artifact)
        .flatMap(_.releases.headOption.map(_.reference))
    ))
  }
}