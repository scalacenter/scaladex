package scaladex.core.service

import java.nio.file.Path

import scala.concurrent.Future

import scaladex.core.model.Project
import scaladex.core.model.data.LocalPomRepository

trait LocalStorageApi {
  def saveProjectSettings(ref: Project.Reference, userData: Project.Settings): Future[Unit]
  def getAllProjectSettings(): Map[Project.Reference, Project.Settings]
  def saveAllProjectSettings(projectSettings: Map[Project.Reference, Project.Settings]): Unit

  def createTempFile(content: String, prefix: String, suffix: String): Path
  def deleteTempFile(path: Path): Unit
  def savePom(content: String, sha1: String, repository: LocalPomRepository): Unit
}
