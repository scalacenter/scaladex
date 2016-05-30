package ch.epfl.scala.index
package client
package rpc

import upickle.default.{Reader, Writer, write => uwrite, read => uread}
import scala.concurrent.Future

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom

object AutowireClient extends autowire.Client[String, Reader, Writer]{
  override def doCall(req: Request): Future[String] = {
    dom.ext.Ajax.post(
      url = "/autowire/" + req.path.mkString("/"),
      data = write(req.args)
    ).map(_.responseText)
  }

  def read[T: Reader](p: String) = uread[T](p)
  def write[T: Writer](r: T) = uwrite(r)
}