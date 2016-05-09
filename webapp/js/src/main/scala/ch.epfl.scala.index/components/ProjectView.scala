package ch.epfl.scala.index
package components

import css._

import japgolly.scalajs.react._, vdom.all._

import scalacss.Defaults._
import scalacss.ScalaCssReact._

object ProjectView {
  object Style extends StyleSheet.Inline {
    import dsl._
  }

  val component = ReactComponentB[ProjectPage]("Project View")
    .render_P{ case ProjectPage(g, a) =>
      div(
        Header(),
        div(s"$g/$a")
      )
    }
    .build
}