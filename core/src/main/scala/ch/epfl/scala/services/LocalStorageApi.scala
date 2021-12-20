package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.newModel.Project

trait LocalStorageApi {
  def saveDataForm(ref: Project.Reference, userData: Project.DataForm): Future[Unit]
  def allDataForms(): Map[Project.Reference, Project.DataForm]
  def saveAllDataForms(dataForms: Map[Project.Reference, Project.DataForm]): Unit
}
