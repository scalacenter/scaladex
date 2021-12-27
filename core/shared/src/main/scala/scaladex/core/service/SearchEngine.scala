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
  def insert(project: ProjectDocument): Future[Unit]
  def delete(reference: Project.Reference): Future[Unit]

  def find(params: SearchParams): Future[Page[ProjectHit]]
  def autocomplete(params: SearchParams): Future[Seq[ProjectDocument]]

  def getTopics(params: SearchParams): Future[Seq[(String, Long)]]
  def getPlatformTypes(params: SearchParams): Future[Seq[(Platform.PlatformType, Long)]]
  def getScalaVersions(params: SearchParams): Future[Seq[(String, Long)]]
  def getScalaJsVersions(params: SearchParams): Future[Seq[(BinaryVersion, Long)]]
  def getScalaNativeVersions(params: SearchParams): Future[Seq[(BinaryVersion, Long)]]
  def getSbtVersions(params: SearchParams): Future[Seq[(BinaryVersion, Long)]]
}
