package ch.epfl.scala.index.search.mapping

import ch.epfl.scala.index.model.Release
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.ScalaTarget

case class ReleaseRef(
    organization: String,
    repository: String,
    artifact: String,
    version: String,
    target: Option[String]
) {
  def toReference: Release.Reference = Release.Reference(
    organization = organization,
    repository = repository,
    artifact = artifact,
    version = SemanticVersion.tryParse(version).get,
    target = target.flatMap(ScalaTarget.parse)
  )
}

object ReleaseRef {
  def apply(ref: Release.Reference): ReleaseRef = ReleaseRef(
    organization = ref.organization,
    repository = ref.repository,
    artifact = ref.artifact,
    version = ref.version.toString(),
    target = ref.target.map(_.encode)
  )
}
