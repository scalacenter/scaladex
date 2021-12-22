package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.Project

trait LocalStorageApi {
  def saveDataForm(ref: Project.Reference, userData: Project.DataForm): Future[Unit]
  def allDataForms(): Map[Project.Reference, Project.DataForm]
  def saveAllDataForms(dataForms: Map[Project.Reference, Project.DataForm]): Unit
}
