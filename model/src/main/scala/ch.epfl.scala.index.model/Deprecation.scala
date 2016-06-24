package ch.epfl.scala.index.model

import ch.epfl.scala.index.model.misc.ISO_8601_Date

/**
 * Deprecation class to mark an artifact as deprecated and point to a bunch of
 * alternatives instead.
 *
 * @param since the date since the artifact is deprecated
 * @param useInstead a list of alternate Artifact references
 */
case class Deprecation(
  since: Option[ISO_8601_Date] = None,
  useInstead: Set[Artifact.Reference] = Set()
)
