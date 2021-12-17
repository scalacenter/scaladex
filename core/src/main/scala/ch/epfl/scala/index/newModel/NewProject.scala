package ch.epfl.scala.index.newModel

import java.time.Instant

import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubRepo
import ch.epfl.scala.index.model.misc.GithubStatus
import ch.epfl.scala.index.model.misc.TwitterSummaryCard
import ch.epfl.scala.index.newModel.NewProject._

// TODO: document NewProject fields
case class NewProject(
    organization: Organization,
    repository: Repository,
    created: Option[Instant], // equivalent to the first release date
    githubStatus: GithubStatus,
    githubInfo: Option[GithubInfo],
    dataForm: DataForm
) {

  val reference: Reference = Reference(organization, repository)
  val githubRepo: GithubRepo = GithubRepo(organization.value, repository.value)
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
}

object NewProject {

  case class Reference(organization: Organization, repository: Repository) extends Ordered[Reference] {
    val githubRepo: GithubRepo =
      GithubRepo(organization.value, repository.value)
    override def toString: String = s"$organization/$repository"

    override def compare(that: Reference): Int =
      Reference.ordering.compare(this, that)
  }
  object Reference {
    def from(org: String, repo: String): Reference =
      Reference(Organization(org), Repository(repo))

    def from(string: String): Reference =
      string.split('/') match {
        case Array(org, repo) => from(org, repo)
      }

    implicit val ordering: Ordering[Reference] =
      Ordering.by(ref => (ref.organization.value, ref.repository.value))
  }
  def default(
      ref: NewProject.Reference,
      created: Option[Instant] = None,
      githubInfo: Option[GithubInfo] = None,
      dataForm: Option[DataForm] = None,
      now: Instant = Instant.now()
  ): NewProject =
    NewProject(
      ref.organization,
      ref.repository,
      githubStatus = githubInfo.map(_ => GithubStatus.Ok(now)).getOrElse(GithubStatus.Unknown(now)),
      githubInfo = githubInfo,
      created = created,
      dataForm = dataForm.getOrElse(DataForm.default)
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
      beginnerIssuesLabel: Option[String]
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
      beginnerIssuesLabel = None
    )
  }
  case class DocumentationLink(label: String, link: String)
  object DocumentationLink {
    def from(label: String, link: String): Option[DocumentationLink] =
      if (label.isEmpty || link.isEmpty) None
      else Some(DocumentationLink(label, link))

  }
}
