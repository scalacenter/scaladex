package scaladex.data

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.json4s._

/**
 * Scope serializer, since Scope is not a case class json4s can't handle this by default
 */
object DateTimeSerializer
    extends CustomSerializer[DateTime](format =>
      (
        {
          case JString(dateTime) =>
            val parser = ISODateTimeFormat.dateTimeParser
            parser.parseDateTime(dateTime)
        },
        {
          case dateTime: DateTime =>
            val formatter = ISODateTimeFormat.dateTime
            JString(formatter.print(dateTime))
        }
      )
    )
