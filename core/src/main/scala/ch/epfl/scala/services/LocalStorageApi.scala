package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.ProjectForm
import ch.epfl.scala.index.newModel.NewProject

trait LocalStorageApi {
  def saveDataForm(
      ref: NewProject.Reference,
      userData: ProjectForm
  ): Future[Unit]
}
