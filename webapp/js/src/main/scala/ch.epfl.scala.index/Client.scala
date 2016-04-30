package ch.epfl.scala.index

import autowire._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom
import scalajs.js.annotation.JSExport

import scalatags.JsDom.all._

@JSExport
object Client {
  @JSExport
  def main(): Unit = {
    val box = input(`type`:="text", placeholder:="Type your name here!").render
    val output = div.render

    def update() = {
      AutowireClient[Api].search(box.value).call().onSuccess{ case (totalSize, artifacts) ⇒
        render(totalSize, artifacts)  
      }
    }

    def render(totalSize: Int, artifacts: List[Artifact]) = {
      output.innerHTML = ""
      output.appendChild(
        div(
          span(totalSize),
          ul(artifacts.map(
            artifact => li(artifact.toString())
          ))
        ).render
      )
    }

    box.onkeyup = _ => {
      update()
    }

    dom.document.body.appendChild(
      div(
        box,
        output
      ).render
    )

    ()
  }
}


import upickle.default.{Reader, Writer, write => uwrite, read => uread}
import scala.concurrent.Future


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

