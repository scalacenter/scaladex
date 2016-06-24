package ch.epfl.scala.index.model
package release

import misc.GeneralReference

/**
 * Dependency trait to mark a class as a dependency
 */
trait Dependency {

  val dependency: GeneralReference
  val scope: Option[Scope]
}
