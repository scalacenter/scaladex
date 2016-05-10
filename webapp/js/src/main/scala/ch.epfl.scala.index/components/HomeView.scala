package ch.epfl.scala.index
package components

import japgolly.scalajs.react._, vdom.all._

import scalacss.Defaults._
import scalacss.ScalaCssReact._

object HomeView {
  object Style extends StyleSheet.Inline {
    import dsl._
    
    val user = style(
      
    )
  }

  val component = ReactComponentB.static("Home View",
    p(Style.user, "")
  ).build

  def apply() = component()
}