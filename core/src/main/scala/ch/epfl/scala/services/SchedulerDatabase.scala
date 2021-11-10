package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.ProjectDependency

trait SchedulerDatabase {
  def getAllProjectRef(): Future[Seq[NewProject.Reference]]
  def getAllProjectDependencies(): Future[Seq[ProjectDependency]]
  def updateCreatedInProjects(ref: NewProject.Reference): Future[Unit]
  def insertProjectDependencies(projectDependencies: Seq[ProjectDependency]): Future[Int]
}
