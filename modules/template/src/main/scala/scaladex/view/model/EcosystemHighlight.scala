package scaladex.view.model

import scaladex.core.model.Url
import scaladex.core.model.Version

final case class EcosystemVersion(version: Version, libraryCount: Int, search: Url)

object EcosystemVersion {
  val ordering: Ordering[EcosystemVersion] = Ordering.by(_.version)
}

final case class EcosystemHighlight(
    ecosystem: String,
    currentVersion: EcosystemVersion,
    otherVersions: Seq[EcosystemVersion]
)

object EcosystemHighlight {
  def apply(ecosystem: String, allVersions: Seq[EcosystemVersion]): Option[EcosystemHighlight] = {
    val sortedVersions = allVersions.sorted(EcosystemVersion.ordering.reverse)
    sortedVersions.headOption.map(EcosystemHighlight(ecosystem, _, sortedVersions.tail))
  }
}
