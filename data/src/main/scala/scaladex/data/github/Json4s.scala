package scaladex.data.github

import org.json4s.DefaultFormats
import org.json4s.native
import org.json4s.native.Serialization

object Json4s {
  implicit val formats: DefaultFormats.type = DefaultFormats
  implicit val serialization: Serialization.type = native.Serialization
}
