package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.release.BinaryVersion
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.search.Page
import ch.epfl.scala.search.ProjectDocument
import ch.epfl.scala.search.ProjectHit
import ch.epfl.scala.search.SearchParams

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
