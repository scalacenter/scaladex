package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.Category
import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Project
import scaladex.core.model.search.Page
import scaladex.core.model.search.ProjectDocument
import scaladex.core.model.search.ProjectHit
import scaladex.core.model.search.SearchParams

trait SearchEngine {
  // Synchronizer
  def insert(project: ProjectDocument): Future[Unit]
  def delete(reference: Project.Reference): Future[Unit]

  // Front page
  def count(): Future[Long]
  def countByTopics(limit: Int): Future[Seq[(String, Long)]]
  def countByLanguages(limit: Int): Future[Seq[(Language, Long)]]
  def countByPlatforms(limit: Int): Future[Seq[(Platform, Long)]]
  def getMostDependedUpon(limit: Int): Future[Seq[ProjectDocument]]
  def getLatest(limit: Int): Future[Seq[ProjectDocument]]

  // Search Page
  def find(params: SearchParams): Future[Page[ProjectHit]]
  def autocomplete(params: SearchParams, limit: Int): Future[Seq[ProjectDocument]]
  def countByTopics(params: SearchParams, limit: Int): Future[Seq[(String, Long)]]
  def countByLanguages(params: SearchParams, limit: Int): Future[Seq[(Language, Long)]]
  def countByPlatforms(params: SearchParams, limit: Int): Future[Seq[(Platform, Long)]]

  // Explore page
  def getByCategory(category: Category, limit: Int): Future[Seq[ProjectDocument]]
}
