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
  def userInfo(): Option[UserInfo] = userState.map(_.user)
  def find(q: String, page: PageIndex): Future[(Pagination, List[Project])] = {
    val perPage = 25
    esClient.execute {
      search
        .in(indexName / collectionName)
        .query(q)
        .start(perPage * page)
        .limit(perPage)
    }.map(r => (
      Pagination(
        current = page,
        total = Math.ceil(r.totalHits / perPage.toDouble).toInt
      ),
      r.as[Project].toList
    ))
  }
  def projectPage(artifact: Artifact.Reference): Future[Option[(Project, Option[GithubReadme])]] = {
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
    }.map(r => r.as[Project].headOption).flatMap{ 
      case Some(project) => 
        github.fetchReadme(project.github).map(
          readme => Some((project, readme))
        )
      case None => Future.successful(None)
    }
  }
}