package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.misc.GithubInfo

case class NewProject(
    organization: String,
    repository: String,
    githubInfo: Option[GithubInfo]
) {

  val reference: Project.Reference = Project.Reference(organization, repository)
}

object NewProject {

  def from(p: Project): NewProject =
    NewProject(
      organization = p.organization,
      repository = p.repository,
      githubInfo = p.github
    )

}
