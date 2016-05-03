package ch.epfl.scala.index

import scala.scalajs.js.JSApp

import org.scalajs.dom
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import japgolly.scalajs.react._, vdom.prefix_<^._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.extra.router._

object HomePage {
  val component = ReactComponentB.static("Home",
    <.h1("Home")
  ).build
}

object Client extends JSApp {

  sealed trait Page
  case object Home extends Page

  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._
    (trimSlashes | staticRoute(root, Home) ~> render(HomePage.component()))
      .notFound(redirectToPage(Home)(Redirect.Replace))
      .renderWith(layout)
      .verify(Home)
  }

  def layout(c: RouterCtl[Page], r: Resolution[Page]) =
    <.div(
      <.div(^.cls := "container", r.render()))

  @JSExport
  override def main(): Unit = {
    dom.console.info("Router logging is enabled. Enjoy!")
    val router = Router(BaseUrl.fromWindowOrigin_/, routerConfig.logToConsole)
    router() render dom.document.body
    ()
  }
}