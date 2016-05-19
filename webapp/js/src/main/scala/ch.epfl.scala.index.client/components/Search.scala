package ch.epfl.scala.index
package client
package components

import api._
import model.{Project, Artifact}

import scala.language.postfixOps

import autowire._
import rpc._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import japgolly.scalajs.react._, vdom.all._
import japgolly.scalajs.react.extra.router._

import scalacss.Defaults._
import scalacss.ScalaCssReact._

object Search {
  private[Search] case class SearchState(query: String, pagination: Pagination, projects: List[Project])

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

  private def target(artifact: Artifact) = {
    ProjectPage(
      artifact.reference.organization,
      artifact.reference.name
    )
  }

  private val ProjectList = ReactComponentB[(List[Project], Pagination, RouterCtl[Page])]("ProjectList")
    .render_P{ case (projects, pagination, ctl) =>
      ul(Style.projectList)(projects.map(project =>
        li(Style.projectElem)(
          ul(project.artifacts.map(artifact =>
            li(
              a(
                Style.projectLink, 
                href := ctl.urlFor(target(artifact)).value,
                ctl.setOnLinkClick(target(artifact)))(
                s"${project.reference.organization}/${artifact.reference.name}"
              )              
            )
          ))
        )
      ))
    }.build

  private[Search] class Backend($: BackendScope[Unit, (SearchState, RouterCtl[Page])]) {
    def onTextChange(e: ReactEventI) = {
      e.extract(_.target.value)(value =>
        for {
        _ <- $.modState{ case (SearchState(_, pagination, projects), ctl) => 
            (SearchState(value, pagination, projects), ctl)
          }
          _ <- Callback.future {
            AutowireClient[Api].find(value, page = 0).call().map{ case (pagination, projects) => 
              $.modState{ case (SearchState(query, _, _), ctl) => 
                (SearchState(query, pagination, projects), ctl)
              }
            }
          }
        } yield ()
      )
    }

    def render(state: (SearchState, RouterCtl[Page])) = {
      val (SearchState(filter, pagination, projects), ctl) = state
      div(
        ProjectSearch((filter, this)),
        ProjectList((projects, pagination, ctl))
      ) 
    }
  }

  def component(ctl: RouterCtl[Page]) = 
    ReactComponentB[Unit]("ProjectSearchApp")
      .initialState((SearchState("", Pagination(0, 0), Nil), ctl))
      .renderBackend[Backend]
      .build

  def apply(ctl: RouterCtl[Page]) = {
    val a = component(ctl)
    a()
  }

}