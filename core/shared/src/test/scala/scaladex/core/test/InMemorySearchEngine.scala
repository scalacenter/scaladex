package scaladex.core.test

import scala.collection.mutable
import scala.concurrent.Future

import scaladex.core.model.BinaryVersion
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.search.Page
import scaladex.core.model.search.ProjectDocument
import scaladex.core.model.search.ProjectHit
import scaladex.core.model.search.SearchParams
import scaladex.core.service.SearchEngine

class InMemorySearchEngine extends SearchEngine {
  val allDocuments: mutable.Map[Project.Reference, ProjectDocument] = mutable.Map.empty

  override def insert(project: ProjectDocument): Future[Unit] = {
    allDocuments.update(project.reference, project)
    Future.successful(())
  }

  override def delete(reference: Project.Reference): Future[Unit] = ???

  override def find(params: SearchParams): Future[Page[ProjectHit]] = ???

  override def autocomplete(params: SearchParams): Future[Seq[ProjectDocument]] = ???

  override def getTopics(params: SearchParams): Future[Seq[(String, Long)]] = ???

  override def getPlatformTypes(params: SearchParams): Future[Seq[(Platform.PlatformType, Long)]] = ???

  override def getScalaVersions(params: SearchParams): Future[Seq[(String, Long)]] = ???

  override def getScalaJsVersions(params: SearchParams): Future[Seq[(BinaryVersion, Long)]] = ???

  override def getScalaNativeVersions(params: SearchParams): Future[Seq[(BinaryVersion, Long)]] = ???

  override def getSbtVersions(params: SearchParams): Future[Seq[(BinaryVersion, Long)]] = ???

}
