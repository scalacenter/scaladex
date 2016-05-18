package ch.epfl.scala.index

import components._

import org.scalajs.dom
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

import japgolly.scalajs.react._, vdom.all._
import japgolly.scalajs.react.extra.router._

sealed trait Page
case object Home extends Page
case class ProjectPage(groupId: String, artifactId: String) extends Page

object Client extends JSApp {
  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    val part = string("[a-zA-Z0-9-_\\.]+")

    (trimSlashes 
    | staticRoute(root, Home) ~> render(HomeView())
    | dynamicRouteCT(("projects" / part / part).caseClass[ProjectPage]) ~> 
        dynRender(ProjectView.component(_))
    )
      .notFound(redirectToPage(Home)(Redirect.Replace))
      .renderWith(layout)
      .verify(Home)
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