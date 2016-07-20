package ch.epfl.scala.index.data.github

import org.json4s.{DefaultFormats, native}

object Json4s {
  implicit val formats       = DefaultFormats
  implicit val serialization = native.Serialization
}
