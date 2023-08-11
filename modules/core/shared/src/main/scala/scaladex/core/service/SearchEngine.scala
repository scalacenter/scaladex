package scaladex.core.service

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

trait SearchEngine {
  // Synchronizer
  def insert(project: ProjectDocument): Future[Unit]
  def delete(reference: Project.Reference): Future[Unit]

  // Front page
  def count(): Future[Int]
  def countByTopics(limit: Int): Future[Seq[TopicCount]]
  def countByLanguages(): Future[Seq[(Language, Int)]]
  def countByPlatforms(): Future[Seq[(Platform, Int)]]
  def getMostDependedUpon(limit: Int): Future[Seq[ProjectDocument]]
  def getLatest(limit: Int): Future[Seq[ProjectDocument]]

  // Old Search API
  def find(
      query: String,
      binaryVersion: Option[BinaryVersion],
      cli: Boolean,
      page: PageParams
  ): Future[Page[ProjectDocument]]

  // Search Page
  def find(params: SearchParams, page: PageParams): Future[Page[ProjectHit]]
  def autocomplete(params: SearchParams, limit: Int): Future[Seq[ProjectDocument]]
  def countByTopics(params: SearchParams, limit: Int): Future[Seq[(String, Int)]]
  def countByLanguages(params: SearchParams): Future[Seq[(Language, Int)]]
  def countByPlatforms(params: SearchParams): Future[Seq[(Platform, Int)]]

  // Awesome pages
  def find(category: Category, params: AwesomeParams, page: PageParams): Future[Page[ProjectDocument]]
  def countByLanguages(category: Category, params: AwesomeParams): Future[Seq[(Language, Int)]]
  def countByPlatforms(category: Category, params: AwesomeParams): Future[Seq[(Platform, Int)]]
}
