package ch.epfl.scala.index.newModel

import ch.epfl.scala.index.model.release.MavenReference

/**
 * Dependency model
 *
 * @param source the maven reference of the source, ex: scaladex
 * @param target the maven reference of one of the dependant libraries of the source, ex: doobie
 */

case class NewDependency(
    source: MavenReference,
    target: MavenReference,
    scope: Option[String]
)

object NewDependency {}
