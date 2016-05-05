package ch.epfl.scala.index

import scala.scalajs.js.JSApp

import org.scalajs.dom
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport

import japgolly.scalajs.react._, vdom.all._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.extra.router._

import autowire._
import upickle.default.{Reader, Writer, write => uwrite, read => uread}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AutowireClient extends autowire.Client[String, Reader, Writer]{
  override def doCall(req: Request): Future[String] = {
    dom.ext.Ajax.post(
      url = "/api/" + req.path.mkString("/"),
      data = write(req.args)
    ).map(_.responseText)
  }

  def read[T: Reader](p: String) = uread[T](p)
  def write[T: Writer](r: T) = uwrite(r)
}

case class SearchState(filter: String, projects: List[Project])

object Search {
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

  class Backend($: BackendScope[Unit, SearchState]) {
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
    ReactDOM.render(
      Router(BaseUrl.fromWindowOrigin_/, routerConfig.logToConsole)(),
      dom.document.body
    )
    ()
  }
}