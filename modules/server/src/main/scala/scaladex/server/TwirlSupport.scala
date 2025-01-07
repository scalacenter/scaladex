package scaladex.server

// https://github.com/btomala/akka-http-twirl

import org.apache.pekko.http.scaladsl.marshalling.Marshaller
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.model.MediaType
import org.apache.pekko.http.scaladsl.model.MediaTypes.*
import play.twirl.api.Html
import play.twirl.api.Txt
import play.twirl.api.Xml

object TwirlSupport extends TwirlSupport

trait TwirlSupport:

  /** Serialize Twirl `Html` to `text/html`. */
  given ToEntityMarshaller[Html] = twirlMarshaller[Html](`text/html`)

  /** Serialize Twirl `Txt` to `text/plain`. */
  given ToEntityMarshaller[Txt] = twirlMarshaller[Txt](`text/plain`)

  /** Serialize Twirl `Xml` to `text/xml`. */
  given ToEntityMarshaller[Xml] = twirlMarshaller[Xml](`text/xml`)

  /** Serialize Twirl formats to `String`. */
  protected def twirlMarshaller[A <: AnyRef](contentType: MediaType): ToEntityMarshaller[A] =
    Marshaller.StringMarshaller.wrap(contentType)(_.toString)
end TwirlSupport
