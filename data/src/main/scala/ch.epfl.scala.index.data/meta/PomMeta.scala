package ch.epfl.scala.index.data.meta

import java.time.Instant

import ch.epfl.scala.index.data.maven.ReleaseModel
import ch.epfl.scala.index.model.release._


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
