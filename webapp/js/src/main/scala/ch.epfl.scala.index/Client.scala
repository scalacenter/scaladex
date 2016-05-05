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

case class SearchState(filter: String, artifacts: List[Artifact])

object Search {
  val SearchBar = ReactComponentB[(String, Backend)]("SearchBar")
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

  val ArtifactList = ReactComponentB[List[Artifact]]("SearchList")
    .render_P(artifacts =>
      ul(artifacts.map( artifact =>
        li(artifact.name)
      ))
    ).build

  class Backend($: BackendScope[Unit, SearchState]) {
    def onTextChange(e: ReactEventI) = {
      e.extract(_.target.value)(value =>
        Callback.future {
          AutowireClient[Api].find(value).call().map{ case (total, artifacts) => 
            $.modState(s => SearchState(value, artifacts))
          }
        }
      )
    }

    def render(state: SearchState) = {
      val SearchState(filter, artifacts) = state
      div(
        SearchBar((filter, this)),
        ArtifactList(artifacts)
      ) 
    }
  }

  val ArtifactApp = ReactComponentB[Unit]("ArtifactApp")
    .initialState(SearchState("com.scalakata", Nil))
    .renderBackend[Backend]
    .componentDidMount(scope => Callback.future {
      AutowireClient[Api].find("com.scalakata").call().map{ case (total, artifacts) => 
        scope.modState(s => SearchState(s.filter, artifacts))
      }
    })
    .build

  val component = ArtifactApp
}

object Client extends JSApp {

  sealed trait Page
  case object Home extends Page

  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._
    (trimSlashes 
    | staticRoute(root, Home) ~> render(Search.component())
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