package ch.epfl.scala.index
package data
package bintray

import spray.json._
import java.nio.file.Path

import com.github.nscala_time.time.Imports._

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

case class BintraySearch(
  sha1: String,
  sha256: Option[String],
  `package`: String,
  name: String,
  path: String,
  size: Int,
  version: String,
  owner: String,
  repo: String,
  created: DateTime
)

trait BintrayProtocol extends DefaultJsonProtocol {
  implicit object DateTimeFormat extends RootJsonFormat[DateTime] {
    val parser = ISODateTimeFormat.dateTimeParser
    val formatter = ISODateTimeFormat.dateTime
    def write(obj: DateTime): JsValue = JsString(formatter.print(obj))
    def read(json: JsValue): DateTime = json match {
      case JsString(s) => 
        try {
          parser.parseDateTime(s)
        }
        catch { case scala.util.control.NonFatal(e) => error(e.toString) }
      case _ => error(json.toString())
    }

    def error(v: Any): DateTime = {
      val example = formatter.print(0)
      deserializationError(f"'$v' is not a valid date value. Dates must be in compact ISO-8601 format, e.g. '$example'")
    }
  }
  implicit val bintraySearchFormat = jsonFormat10(BintraySearch)
}

object BintrayMeta extends BintrayProtocol {
  def sortedByCreated(path: Path): List[BintraySearch] = {
    val source = scala.io.Source.fromFile(path.toFile)
    val ret =
      source.mkString.split(nl).
      toList
      
    source.close()

    ret.map(_.parseJson.convertTo[BintraySearch]).sortBy(_.created)(Descending)
  }
}