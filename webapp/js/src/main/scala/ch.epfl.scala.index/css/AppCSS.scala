package ch.epfl.scala.index
package css

import scalacss.ScalaCssReact._
import scalacss.mutable.GlobalRegistry
import scalacss.Defaults._

object AppCSS {

  def load() = {
    GlobalRegistry.register(
      GlobalStyle
    )
    GlobalRegistry.onRegistration(_.addToDocument())
  }
}
