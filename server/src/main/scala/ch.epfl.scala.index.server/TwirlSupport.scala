package ch.epfl.scala.index
package server

// https://github.com/btomala/akka-http-twirl

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.MediaTypes._
import play.twirl.api.Html
import play.twirl.api.Txt
import play.twirl.api.Xml

object TwirlSupport extends TwirlSupport

trait TwirlSupport {

  /** Serialize Twirl `Html` to `text/html`. */
  implicit val twirlHtmlMarshaller: ToEntityMarshaller[Html] =
    twirlMarshaller[Html](`text/html`)

  /** Serialize Twirl `Txt` to `text/plain`. */
  implicit val twirlTxtMarshaller: ToEntityMarshaller[Txt] =
    twirlMarshaller[Txt](`text/plain`)

  /** Serialize Twirl `Xml` to `text/xml`. */
  implicit val twirlXmlMarshaller: ToEntityMarshaller[Xml] =
    twirlMarshaller[Xml](`text/xml`)

  /** Serialize Twirl formats to `String`. */
  protected def twirlMarshaller[A <: AnyRef: Manifest](
      contentType: MediaType
  ): ToEntityMarshaller[A] =
    Marshaller.StringMarshaller.wrap(contentType)(_.toString)

}
