package scaladex.core.test

import scala.collection.mutable
import scala.concurrent.Future

import scaladex.core.model.BinaryVersion
import scaladex.core.model.Category
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.TopicCount
import scaladex.core.model.search._
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

  override def find(params: SearchParams, page: PageParams): Future[Page[ProjectHit]] = {
    val hits = allDocuments.values
      .filter(doc => (params.languages.toSet -- doc.languages).isEmpty)
      .filter(doc => (params.platforms.toSet -- doc.platforms).isEmpty)
      .toSeq
      .map(ProjectHit(_, Seq.empty))
    val res = Page(Pagination(1, 1, hits.size), hits)
    Future.successful(res)
  }

  override def findRefs(params: SearchParams): Future[Seq[Project.Reference]] = {
    val res = allDocuments.values
      .filter(doc => (params.languages.toSet -- doc.languages).isEmpty)
      .filter(doc => (params.platforms.toSet -- doc.platforms).isEmpty)
      .toSeq
      .map(_.reference)
    Future.successful(res)
  }

  override def autocomplete(params: SearchParams, limit: Int): Future[Seq[ProjectDocument]] = ???

  override def countByTopics(params: SearchParams, limit: Int): Future[Seq[(String, Int)]] = ???

  override def countByLanguages(params: SearchParams): Future[Seq[(Language, Int)]] = ???

  override def countByPlatforms(params: SearchParams): Future[Seq[(Platform, Int)]] = ???

  override def find(category: Category, params: AwesomeParams, page: PageParams): Future[Page[ProjectDocument]] = ???

  override def countByLanguages(category: Category, params: AwesomeParams): Future[Seq[(Language, Int)]] = ???

  override def countByPlatforms(category: Category, params: AwesomeParams): Future[Seq[(Platform, Int)]] = ???
}
