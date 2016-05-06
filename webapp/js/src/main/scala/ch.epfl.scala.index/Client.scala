package ch.epfl.scala.index

import components._

import org.scalajs.dom
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

import japgolly.scalajs.react._, vdom.all._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.extra.router._

object Client extends JSApp {

  sealed trait Page
  case object Home extends Page

  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._
    (trimSlashes 
    | staticRoute(root, Home) ~> render(Search.ProjectApp())
    )
      .notFound(redirectToPage(Home)(Redirect.Replace))
      .renderWith(layout)
      .verify(Home)
  }

  def layout(c: RouterCtl[Page], r: Resolution[Page]) =
    div(
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