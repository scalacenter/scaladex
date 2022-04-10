package scaladex.server.util

import play.api.libs.json.Json
import play.api.libs.json.Reads
import scaladex.core.model.search.Page
import scaladex.core.model.search.Pagination

trait PaginatedResponseReader {

  implicit def pageItemsJson[A](implicit items: Reads[A]): Reads[Seq[A]] = Reads.seq(items)
  implicit val paginationJson: Reads[Pagination] = Json.reads[Pagination]
  implicit def pageJson[A](implicit items: Reads[A]): Reads[Page[A]] = Json.reads[Page[A]]

}
