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
  def countByLanguages(): Future[Seq[(Language, Long)]]
  def countByPlatforms(): Future[Seq[(Platform, Long)]]
  def getMostDependedUpon(limit: Int): Future[Seq[ProjectDocument]]
  def getLatest(limit: Int): Future[Seq[ProjectDocument]]

  // Search Page
  def find(params: SearchParams): Future[Page[ProjectHit]]
  def autocomplete(params: SearchParams, limit: Int): Future[Seq[ProjectDocument]]
  def countByTopics(params: SearchParams, limit: Int): Future[Seq[(String, Long)]]
  def countByLanguages(params: SearchParams): Future[Seq[(Language, Long)]]
  def countByPlatforms(params: SearchParams): Future[Seq[(Platform, Long)]]

  // Explore page
  def getAllLanguages(): Future[Seq[Language]]
  def getAllPlatforms(): Future[Seq[Platform]]
  def getByCategory(
      category: Category,
      languages: Seq[Language],
      platforms: Seq[Platform],
      limit: Int
  ): Future[Seq[ProjectDocument]]
}
