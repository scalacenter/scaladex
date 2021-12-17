package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.newModel.NewProject

trait LocalStorageApi {
  def saveDataForm(ref: NewProject.Reference, userData: NewProject.DataForm): Future[Unit]
  def allDataForms(): Map[NewProject.Reference, NewProject.DataForm]
  def saveAllDataForms(dataForms: Map[NewProject.Reference, NewProject.DataForm]): Unit
}
