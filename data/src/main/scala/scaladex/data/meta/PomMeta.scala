package scaladex.data.meta

import java.time.Instant

import scaladex.core.model.Resolver
import scaladex.data.maven.ArtifactModel

case class PomMeta(
    artifactModel: ArtifactModel,
    creationDate: Option[Instant],
    resolver: Option[Resolver]
)

object PomMeta {
  def default(artifactModel: ArtifactModel): PomMeta =
    PomMeta(
      artifactModel = artifactModel,
      creationDate = None,
      resolver = None
    )
}
