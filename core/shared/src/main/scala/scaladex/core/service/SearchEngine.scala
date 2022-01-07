package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.BinaryVersion
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
  def countByPlatformTypes(limit: Int): Future[Seq[(Platform.PlatformType, Long)]]
  def countByScalaVersions(limit: Int): Future[Seq[(String, Long)]]
  def countByScalaJsVersions(limit: Int): Future[Seq[(BinaryVersion, Long)]]
  def countByScalaNativeVersions(limit: Int): Future[Seq[(BinaryVersion, Long)]]
  def countBySbtVersison(limit: Int): Future[Seq[(BinaryVersion, Long)]]
  def getMostDependedUpon(limit: Int): Future[Seq[ProjectDocument]]
  def getLatest(limit: Int): Future[Seq[ProjectDocument]]

  // Search Page
  def find(params: SearchParams): Future[Page[ProjectHit]]
  def autocomplete(params: SearchParams, limit: Int): Future[Seq[ProjectDocument]]
  def countByTopics(params: SearchParams, limit: Int): Future[Seq[(String, Long)]]
  def countByPlatformTypes(params: SearchParams, limit: Int): Future[Seq[(Platform.PlatformType, Long)]]
  def countByScalaVersions(params: SearchParams, limit: Int): Future[Seq[(String, Long)]]
  def countByScalaJsVersions(params: SearchParams, limit: Int): Future[Seq[(BinaryVersion, Long)]]
  def countByScalaNativeVersions(params: SearchParams, limit: Int): Future[Seq[(BinaryVersion, Long)]]
  def countBySbtVersions(params: SearchParams, limit: Int): Future[Seq[(BinaryVersion, Long)]]
}
