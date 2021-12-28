package scaladex.data.meta

import java.time.Instant

import scaladex.core.model.Resolver
import scaladex.data.maven.ReleaseModel

case class PomMeta(
    releaseModel: ReleaseModel,
    creationDate: Option[Instant],
    resolver: Option[Resolver]
)

object PomMeta {
  def default(releaseModel: ReleaseModel): PomMeta =
    PomMeta(
      releaseModel = releaseModel,
      creationDate = None,
      resolver = None
    )
}
