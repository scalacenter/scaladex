package ch.epfl.scala.services

import scala.concurrent.Future

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.newModel.NewDependency
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease

trait DatabaseApi {
  def insertProject(p: NewProject): Future[Unit]
  def insertOrUpdateProject(p: NewProject): Future[Unit]
  def findProject(projectRef: Project.Reference): Future[Option[NewProject]]
  def insertReleases(r: Seq[NewRelease]): Future[Int]
  def insertDependencies(dependencies: Seq[NewDependency]): Future[Int]
  def countProjects(): Future[Long]
  def countReleases(): Future[Long]
  def countDependencies(): Future[Long]
}