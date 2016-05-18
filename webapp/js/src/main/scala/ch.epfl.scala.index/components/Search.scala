package ch.epfl.scala.index
package components

import scala.language.postfixOps

import autowire._
import rpc._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import japgolly.scalajs.react._, vdom.all._
import japgolly.scalajs.react.extra.router._

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

    val projectList =
      style(
        paddingLeft.`0`
      )

    val projectElem =
      style(
        display.block
      )

    val projectLink =
      style(
        color.white,
        textDecoration := "none"
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

  private def target(project: Project) =
    ProjectPage(project.groupId, project.artifactId)

  private val ProjectList = ReactComponentB[(List[Project], RouterCtl[Page])]("ProjectList")
    .render_P{ case (projects, ctl) =>
      ul(Style.projectList)(projects.map( project =>
        li(Style.projectElem)(
          a(Style.projectLink, 
            href := ctl.urlFor(target(project)).value,
            ctl.setOnLinkClick(target(project)))(
            s"${project.groupId} ${project.artifactId}"
          )
        )
      ))
    }.build

  private[Search] class Backend($: BackendScope[Unit, (SearchState, RouterCtl[Page])]) {
    def onTextChange(e: ReactEventI) = {
      e.extract(_.target.value)(value =>
        Callback.future {
          AutowireClient[Api].find(value).call().map{ case (total, projects) => 
            $.modState{ case (_, ctl) => (SearchState(value, projects), ctl)}
          }
        }
      )
    }

    def render(state: (SearchState, RouterCtl[Page])) = {
      val (SearchState(filter, projects), ctl) = state
      div(
        ProjectSearch((filter, this)),
        ProjectList((projects, ctl))
      ) 
    }
  }

  def component(ctl: RouterCtl[Page]) = 
    ReactComponentB[Unit]("ProjectSearchApp")
      .initialState((SearchState("", Nil), ctl))
      .renderBackend[Backend]
      .build

  def apply(ctl: RouterCtl[Page]) = {
    val a = component(ctl)
    a()
  }

}