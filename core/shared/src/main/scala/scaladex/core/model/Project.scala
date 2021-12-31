package scaladex.core.model

import java.time.Instant

import scaladex.core.model.Project._
// TODO: document NewProject fields
case class Project(
    organization: Organization,
    repository: Repository,
    creationDate: Option[Instant], // date of the first known artifact
    githubStatus: GithubStatus,
    githubInfo: Option[GithubInfo],
    settings: Settings
) {

  val reference: Reference = Reference(organization, repository)
  def hasCli: Boolean = settings.cliArtifacts.nonEmpty

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

object Project {

  case class Reference(organization: Organization, repository: Repository) extends Ordered[Reference] {
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
      ref: Project.Reference,
      creationDate: Option[Instant] = None,
      githubInfo: Option[GithubInfo] = None,
      settings: Option[Settings] = None,
      now: Instant = Instant.now()
  ): Project =
    Project(
      ref.organization,
      ref.repository,
      githubStatus = githubInfo.map(_ => GithubStatus.Ok(now)).getOrElse(GithubStatus.Unknown(now)),
      githubInfo = githubInfo,
      creationDate = creationDate,
      settings = settings.getOrElse(Settings.default)
    )

  case class Settings(
      defaultStableVersion: Boolean,
      defaultArtifact: Option[Artifact.Name],
      strictVersions: Boolean,
      customScalaDoc: Option[String],
      documentationLinks: List[DocumentationLink],
      deprecated: Boolean,
      contributorsWanted: Boolean,
      artifactDeprecations: Set[Artifact.Name],
      cliArtifacts: Set[Artifact.Name],
      primaryTopic: Option[String],
      beginnerIssuesLabel: Option[String]
  )

  case class Organization private (value: String) extends AnyVal {
    override def toString(): String = value
    def isEmpty(): Boolean = value.isEmpty
  }
  case class Repository private (value: String) extends AnyVal {
    override def toString(): String = value
    def isEmpty(): Boolean = value.isEmpty
  }
  object Organization {
    def apply(v: String): Organization = new Organization(v.toLowerCase())
  }
  object Repository {
    def apply(v: String): Repository = new Repository(v.toLowerCase())
  }

  object Settings {
    val default: Settings = Settings(
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
