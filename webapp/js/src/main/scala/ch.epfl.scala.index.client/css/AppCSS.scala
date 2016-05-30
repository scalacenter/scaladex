package ch.epfl.scala.index
package client
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
      HomeView.Style,
      ProjectView.Style,
      Search.Style,
      User.Style
    )
    GlobalRegistry.onRegistration(_.addToDocument())
  }
}
