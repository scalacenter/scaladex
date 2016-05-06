package ch.epfl.scala.index
package components

import autowire._
import rpc._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import japgolly.scalajs.react._, vdom.all._

object Search {
  private[Search] case class SearchState(filter: String, projects: List[Project])

  val ProjectSearch = ReactComponentB[(String, Backend)]("ProjectSearch")
    .render_P { case (s, b) =>
      form(
        input.text(
          placeholder := "Search Projects",
          value       := s,
          onChange   ==> b.onTextChange
        )
      )
    }
    .build

  val ProjectList = ReactComponentB[List[Project]]("ProjectList")
    .render_P(projects =>
      ul(projects.map( project =>
        li(project.artifactId)
      ))
    ).build

  final class Backend($: BackendScope[Unit, SearchState]) {
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

  val ProjectApp = ReactComponentB[Unit]("ArtifactApp")
    .initialState(SearchState("com.scalakata", Nil))
    .renderBackend[Backend]
    .componentDidMount(scope => Callback.future {
      AutowireClient[Api].find("com.scalakata").call().map{ case (total, artifacts) => 
        scope.modState(s => SearchState(s.filter, artifacts))
      }
    })
    .build
}