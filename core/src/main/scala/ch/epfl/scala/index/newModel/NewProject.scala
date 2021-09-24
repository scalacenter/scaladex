package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.misc.{GithubInfo, GithubIssue, TwitterSummaryCard}
import ch.epfl.scala.index.newModel.NewProject._

// TODO: document NewProject fields
case class NewProject(
    organization: Organization,
    repository: Repository,
    githubInfo: Option[NewGithubInfo],
    esId: Option[String],
    // form data
    dataForm: DataForm
) {
  val reference: Project.Reference =
    Project.Reference(organization.value, repository.value)
  def hasCli: Boolean = dataForm.cliArtifacts.nonEmpty

  /**
   * This is used in twitter to render the card of a scaladex project link.
   */
  def twitterSummaryCard: TwitterSummaryCard = TwitterSummaryCard(
    "@scala_lang",
    repository.toString(),
    githubInfo.flatMap(_.description).getOrElse(""),
    githubInfo.flatMap(_.logo)
  )
  //val created: datetime = firstRelease.released
  //val lastUpdated: datetime = lastReleaseAdd.released

  def update(newGithubInfo: Option[NewGithubInfo]): NewProject =
    copy(githubInfo = newGithubInfo)
  def contributorsWanted: Boolean = dataForm.contributorsWanted
}

object NewProject {
  def defaultProject(
      org: String,
      repo: String,
      githubInfo: Option[NewGithubInfo],
      formData: DataForm = DataForm.default
  ): NewProject =
    NewProject(
      Organization(org),
      repository = Repository(repo),
      githubInfo = githubInfo,
      esId = None,
      dataForm = formData
    )

  case class DataForm(
      defaultStableVersion: Boolean,
      defaultArtifact: Option[NewRelease.ArtifactName],
      strictVersions: Boolean,
      customScalaDoc: Option[String],
      documentationLinks: List[DocumentationLink],
      deprecated: Boolean,
      contributorsWanted: Boolean,
      artifactDeprecations: Set[NewRelease.ArtifactName],
      cliArtifacts: Set[NewRelease.ArtifactName],
      primaryTopic: Option[String],
      beginnerIssuesLabel: Option[String],
  beginnerIssues: List[GithubIssue],
  selectedBeginnerIssues: List[GithubIssue],
  filteredBeginnerIssues: List[GithubIssue]
  )

  case class Organization(value: String) extends AnyVal {
    override def toString(): String = value
  }
  case class Repository(value: String) extends AnyVal {
    override def toString(): String = value
  }

  object DataForm {
    val default: DataForm = DataForm(
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
      beginnerIssuesLabel = None,
      beginnerIssues = List(),
      selectedBeginnerIssues = List(),
    filteredBeginnerIssues= List()
    )
    def from(p: Project): DataForm = {
      val documentationlinks = p.documentationLinks.map { case (label, link) =>
        DocumentationLink(label, link)
      }
      DataForm(
        defaultStableVersion = p.defaultStableVersion,
        defaultArtifact = p.defaultArtifact.map(NewRelease.ArtifactName),
        strictVersions = p.strictVersions,
        customScalaDoc = p.customScalaDoc,
        documentationLinks = documentationlinks,
        deprecated = p.deprecated,
        contributorsWanted = p.contributorsWanted,
        artifactDeprecations =
          p.artifactDeprecations.map(NewRelease.ArtifactName),
        cliArtifacts = p.cliArtifacts.map(NewRelease.ArtifactName),
        primaryTopic = p.primaryTopic,
        beginnerIssuesLabel = p.github.flatMap(_.beginnerIssuesLabel),
        beginnerIssues = p.github.map(_.beginnerIssues).getOrElse(Nil),
        selectedBeginnerIssues = p.github.map(_.selectedBeginnerIssues).getOrElse(Nil),
        filteredBeginnerIssues = p.github.map(_.filteredBeginnerIssues).getOrElse(Nil)

      )
    }
  }
  case class DocumentationLink(label: String, link: String)
  object DocumentationLink {
    def from(label: String, link: String): Option[DocumentationLink] =
      if (label.isEmpty || link.isEmpty) None
      else Some(DocumentationLink(label, link))

  }

  def from(p: Project): NewProject = {
    NewProject(
      organization = Organization(p.organization),
      repository = Repository(p.repository),
      githubInfo = p.github.map(NewGithubInfo.from),
      esId = p.id,
      dataForm = DataForm.from(p)
    )
  }

}
