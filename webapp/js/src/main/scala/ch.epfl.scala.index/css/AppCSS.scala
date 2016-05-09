package ch.epfl.scala.index
package css

import components._

import scalacss.ScalaCssReact._
import scalacss.mutable.GlobalRegistry
import scalacss.Defaults._


object AppCSS {

  def load() = {
    GlobalRegistry.register(
      GlobalStyle,
      Header.Style,
      ProjectView.Style,
      Search.Style,
      User.Style
    )
    GlobalRegistry.onRegistration(_.addToDocument())
  }
}
