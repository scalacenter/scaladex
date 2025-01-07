package scaladex.view.model

import scaladex.core.model.Url
import scaladex.core.model.Version

final case class EcosystemVersion(version: Version, libraryCount: Int, search: Url)

final case class EcosystemHighlight(
    ecosystem: String,
    currentVersion: EcosystemVersion,
    otherVersions: Seq[EcosystemVersion]
)

object EcosystemHighlight:
  def apply(ecosystem: String, allVersions: Seq[EcosystemVersion]): Option[EcosystemHighlight] =
    val sortedVersions = allVersions.sortBy(_.version)(Version.PreferStable).reverse
    sortedVersions.headOption.map(EcosystemHighlight(ecosystem, _, sortedVersions.tail))
