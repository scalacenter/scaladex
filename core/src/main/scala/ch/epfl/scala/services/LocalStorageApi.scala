package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.ProjectForm

trait LocalStorageApi {
  def saveDataForm(ref: Project.Reference, userData: ProjectForm): Future[Unit]
}
