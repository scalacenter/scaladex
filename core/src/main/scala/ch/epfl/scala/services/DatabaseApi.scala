package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.newModel.NewProject

trait DatabaseApi {
  def insertProject(p: NewProject): Future[NewProject]
  def countProjects(): Future[Long]
}
