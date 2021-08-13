package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.newModel.NewProject._

case class NewProject(
    organization: String,
    repository: String,
    githubInfo: Option[GithubInfo],
    defaultStableVersion: Boolean,
    defaultArtifact: Option[String],
    strictVersions: Boolean,
    customScalaDoc: Option[String],
    documentationLinks: List[DocumentationLink],
    deprecated: Boolean,
    contributorsWanted: Boolean,
    artifactDeprecations: Set[String],
    cliArtifacts: Set[String],
    primaryTopic: Option[String],
    esId: Option[String]
) {

  val formData: NewProject.FormData = NewProject.FormData.apply(this)
  val reference: Project.Reference = Project.Reference(organization, repository)
  val hasCli: Boolean = cliArtifacts.nonEmpty
}

object NewProject {
  def defaultProject(org: String, repo: String): NewProject =
    NewProject(
      org,
      repository = repo,
      githubInfo = None,
      defaultStableVersion = true,
      defaultArtifact = None,
      strictVersions = false,
      customScalaDoc = None,
      documentationLinks = List(),
      deprecated = false,
      contributorsWanted = false,
      artifactDeprecations = Set(),
      cliArtifacts = Set(),
      primaryTopic = None,
      esId = None
    )

  case class FormData(
      defaultStableVersion: Boolean,
      defaultArtifact: Option[String],
      strictVersions: Boolean,
      customScalaDoc: Option[String],
      documentationLinks: List[DocumentationLink],
      deprecated: Boolean,
      contributorsWanted: Boolean,
      artifactDeprecations: Set[String],
      cliArtifacts: Set[String],
      primaryTopic: Option[String]
  )

  object FormData {
    def apply(p: NewProject): FormData =
      FormData(
        p.defaultStableVersion,
        p.defaultArtifact,
        p.strictVersions,
        p.customScalaDoc,
        p.documentationLinks,
        p.deprecated,
        p.contributorsWanted,
        p.artifactDeprecations,
        p.cliArtifacts,
        p.primaryTopic
      )
  }
  case class DocumentationLink(label: String, link: String)

  def from(p: Project): NewProject = {
    val documentationlinks = p.documentationLinks.map { case (label, link) =>
      DocumentationLink(label, link)
    }
    NewProject(
      organization = p.organization,
      repository = p.repository,
      githubInfo = p.github,
      defaultStableVersion = p.defaultStableVersion,
      defaultArtifact = p.defaultArtifact,
      strictVersions = p.strictVersions,
      customScalaDoc = p.customScalaDoc,
      documentationLinks = documentationlinks,
      deprecated = p.deprecated,
      contributorsWanted = p.contributorsWanted,
      artifactDeprecations = p.artifactDeprecations,
      cliArtifacts = p.cliArtifacts,
      primaryTopic = p.primaryTopic,
      esId = p.id
    )
  }

}
