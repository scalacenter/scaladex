package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.Project

case class NewProject(organization: String, repository: String) {

  val reference: Project.Reference = Project.Reference(organization, repository)
}

object NewProject {

  def from(p: Project): NewProject =
    NewProject(
      organization = p.organization,
      repository = p.repository
    )

}
