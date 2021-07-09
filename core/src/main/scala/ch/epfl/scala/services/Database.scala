package ch.epfl.scala.services

import ch.epfl.scala.index.model.Project

trait Database {

  def getProject(id: String): Option[Project]
}
