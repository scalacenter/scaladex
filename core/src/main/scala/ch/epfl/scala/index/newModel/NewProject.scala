package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.newModel.NewProject._

// TODO: document NewProject fields
case class NewProject(
    organization: Organization,
    repository: Repository,
    githubInfo: Option[GithubInfo],
    esId: Option[String],
    // form data
    formData: FormData
) {
  val reference: Project.Reference =
    Project.Reference(organization.value, repository.value)
  val hasCli: Boolean = formData.cliArtifacts.nonEmpty
  //val created: datetime = firstRelease.released
  //val lastUpdated: datetime = lastReleaseAdd.released
  def update(newGithubInfo: Option[GithubInfo]): NewProject =
    copy(githubInfo = newGithubInfo)
}

object NewProject {
  def defaultProject(
      org: String,
      repo: String,
      githubInfo: Option[GithubInfo],
      formData: FormData = FormData.default
  ): NewProject =
    NewProject(
      Organization(org),
      repository = Repository(repo),
      githubInfo = githubInfo,
      esId = None,
      formData = formData
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

  case class Organization(value: String) extends AnyVal
  case class Repository(value: String) extends AnyVal

  object FormData {
    val default: FormData = FormData(
      defaultStableVersion = true,
      defaultArtifact = None,
      strictVersions = false,
      customScalaDoc = None,
      documentationLinks = List(),
      deprecated = false,
      contributorsWanted = false,
      artifactDeprecations = Set(),
      cliArtifacts = Set(),
      primaryTopic = None
    )
    def from(p: Project): FormData = {
      val documentationlinks = p.documentationLinks.map { case (label, link) =>
        DocumentationLink(label, link)
      }
      FormData(
        defaultStableVersion = p.defaultStableVersion,
        defaultArtifact = p.defaultArtifact,
        strictVersions = p.strictVersions,
        customScalaDoc = p.customScalaDoc,
        documentationLinks = documentationlinks,
        deprecated = p.deprecated,
        contributorsWanted = p.contributorsWanted,
        artifactDeprecations = p.artifactDeprecations,
        cliArtifacts = p.cliArtifacts,
        primaryTopic = p.primaryTopic
      )
    }
  }
  case class DocumentationLink(label: String, link: String)

  def from(p: Project): NewProject = {
    NewProject(
      organization = Organization(p.organization),
      repository = Repository(p.repository),
      githubInfo = p.github,
      esId = p.id,
      formData = FormData.from(p)
    )
  }

}
