package ch.epfl.scala.index.model.release

/**
 * Release candidate RC-1 | RC-n
 * @param rc the version of the RC
 */
case class ReleaseCandidate(rc: Long) extends PreRelease
