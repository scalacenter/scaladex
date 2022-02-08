package scaladex.core.test

import scala.collection.mutable
import scala.concurrent.Future

import scaladex.core.model.Category
import scaladex.core.model.Language
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

  override def count(): Future[Long] = ???

  override def countByLanguages(limit: Int): Future[Seq[(Language, Long)]] = ???

  override def countByPlatforms(limit: Int): Future[Seq[(Platform, Long)]] = ???

  override def countByTopics(limit: Int): Future[Seq[(String, Long)]] = ???

  override def getMostDependedUpon(limit: Int): Future[Seq[ProjectDocument]] = ???

  override def getLatest(limit: Int): Future[Seq[ProjectDocument]] = ???

  override def find(params: SearchParams): Future[Page[ProjectHit]] = ???

  override def autocomplete(params: SearchParams, limit: Int): Future[Seq[ProjectDocument]] = ???

  override def countByTopics(params: SearchParams, limit: Int): Future[Seq[(String, Long)]] = ???

  override def countByLanguages(params: SearchParams, limit: Int): Future[Seq[(Language, Long)]] = ???

  override def countByPlatforms(params: SearchParams, limit: Int): Future[Seq[(Platform, Long)]] = ???

  override def getByCategory(category: Category, limit: Int): Future[Seq[ProjectDocument]] = ???

}
