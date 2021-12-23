package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.Project

trait LocalStorageApi {
  def saveProjectSettings(ref: Project.Reference, userData: Project.Settings): Future[Unit]
  def getAllProjectSettings(): Map[Project.Reference, Project.Settings]
  def saveAllProjectSettings(projectSettings: Map[Project.Reference, Project.Settings]): Unit
}
