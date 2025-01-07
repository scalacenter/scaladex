package scaladex.core.api

import scaladex.core.model.Project.*
import scaladex.core.model.*

case class ProjectResponse(
    organization: Organization,
    repository: Repository,
    homepage: Option[Url],
    description: Option[String],
    logo: Option[Url],
    stars: Option[Int],
    forks: Option[Int],
    issues: Option[Int],
    topics: Set[String],
    contributingGuide: Option[Url],
    codeOfConduct: Option[Url],
    license: Option[License],
    defaultArtifact: Option[Artifact.Name],
    customScalaDoc: Option[String],
    documentationLinks: Seq[DocumentationPattern],
    contributorsWanted: Boolean,
    cliArtifacts: Set[Artifact.Name],
    category: Option[Category],
    chatroom: Option[String]
)
