package ch.epfl.scala.index
package components

import scala.language.postfixOps
import scala.concurrent.duration._

import autowire._
import rpc._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import japgolly.scalajs.react._, vdom.all._

import scalacss.Defaults._
import scalacss.ScalaCssReact._

object Search {
  private[Search] case class SearchState(filter: String, projects: List[Project])

  object Style extends StyleSheet.Inline {
    import dsl._

    val searchInput =
      style(
        border.none,
        height(2 em),
        fontSize(1.5 em),
        padding.`0`,
        width(100 %%),
        &.focus(
          border.none,
          outline.none
        ),
        backgroundColor.transparent
      )
  }

  private val ProjectSearch = ReactComponentB[(String, Backend)]("ProjectSearch")
    .render_P { case (s, b) =>
      input.text(
        Style.searchInput,
        placeholder := "Search Projects",
        value       := s,
        onChange   ==> b.onTextChange
      )
    }
    .build

  private val ProjectList = ReactComponentB[List[Project]]("ProjectList")
    .render_P(projects =>
      ul(projects.map( project =>
        li(project.artifactId)
      ))
    ).build

  private[Search] class Backend($: BackendScope[Unit, SearchState]) {
    def onTextChange(e: ReactEventI) = {
      e.extract(_.target.value)(value =>
        Callback.future {
          AutowireClient[Api].find(value).call().map{ case (total, projects) => 
            $.modState(s => SearchState(value, projects))
          }
        }
      )
    }

    def render(state: SearchState) = {
      val SearchState(filter, projects) = state
      div(
        ProjectSearch((filter, this)),
        ProjectList(projects)
      ) 
    }
  }

  def apply() = ReactComponentB[Unit]("ProjectSearchApp")
    .initialState(SearchState("", Nil))
    .renderBackend[Backend]
    .build()
}