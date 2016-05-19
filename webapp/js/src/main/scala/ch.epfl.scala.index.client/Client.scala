package ch.epfl.scala.index
package client

import components._

import org.scalajs.dom
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

import japgolly.scalajs.react._, vdom.all._
import japgolly.scalajs.react.extra.router._

sealed trait Page
case object Home extends Page
case class ProjectPage(
  organization: String,
  artifact: String
) extends Page

object Client extends JSApp {
  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    val part = string("[a-zA-Z0-9-_\\.]+")

    val organization = part
    val artifact = part
    val version = part

    val projectRoute = ("project" / organization / artifact).caseClass[ProjectPage]

    ( trimSlashes
        | staticRoute(root, Home) ~> render(HomeView())
        | dynamicRouteCT(projectRoute) ~> dynRender(ProjectView.component(_))
    ) 
      .notFound(redirectToPage(Home)(Redirect.Replace))
      .renderWith(layout)
      //.verify(Home, ProjectPage("typelevel", "cats"))
  }

  def layout(c: RouterCtl[Page], r: Resolution[Page]) =
    div(
      Header.component(c),
      div(cls := "container", r.render())
    )

  @JSExport
  override def main(): Unit = {
    css.AppCSS.load()
    ReactDOM.render(
      Router(BaseUrl.fromWindowOrigin_/, routerConfig.logToConsole)(),
      dom.document.body
    )
    ()
  }
}