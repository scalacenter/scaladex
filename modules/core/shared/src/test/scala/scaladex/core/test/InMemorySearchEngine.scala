package scaladex.core.test

import scala.collection.mutable
import scala.concurrent.Future

import scaladex.core.model.BinaryVersion
import scaladex.core.model.Category
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.TopicCount
import scaladex.core.model.search.AwesomeParams
import scaladex.core.model.search.Page
import scaladex.core.model.search.PageParams
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

  override def count(): Future[Int] = ???

  override def countByLanguages(): Future[Seq[(Language, Int)]] = ???

  override def countByPlatforms(): Future[Seq[(Platform, Int)]] = ???

  override def countByTopics(limit: Int): Future[Seq[TopicCount]] = ???

  override def getMostDependedUpon(limit: Int): Future[Seq[ProjectDocument]] = ???

  override def getLatest(limit: Int): Future[Seq[ProjectDocument]] = ???

  override def find(
      query: String,
      binaryVersion: Option[BinaryVersion],
      cli: Boolean,
      page: PageParams
  ): Future[Page[ProjectDocument]] = ???

  override def find(params: SearchParams, page: PageParams): Future[Page[ProjectHit]] = ???

  override def autocomplete(params: SearchParams, limit: Int): Future[Seq[ProjectDocument]] = ???

  override def countByTopics(params: SearchParams, limit: Int): Future[Seq[(String, Int)]] = ???

  override def countByLanguages(params: SearchParams): Future[Seq[(Language, Int)]] = ???

  override def countByPlatforms(params: SearchParams): Future[Seq[(Platform, Int)]] = ???

  override def find(category: Category, params: AwesomeParams, page: PageParams): Future[Page[ProjectDocument]] = ???

  override def countByLanguages(category: Category, params: AwesomeParams): Future[Seq[(Language, Int)]] = ???

  override def countByPlatforms(category: Category, params: AwesomeParams): Future[Seq[(Platform, Int)]] = ???
}
