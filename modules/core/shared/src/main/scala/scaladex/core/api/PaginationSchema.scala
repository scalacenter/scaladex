package scaladex.core.api

import scaladex.core.model.search.Page
import scaladex.core.model.search.Pagination

/**
 * An API schema that supports pagination should extend this trait.
 */
trait PaginationSchema extends endpoints4s.algebra.JsonSchemas {

  implicit val paginationSchema: JsonSchema[Pagination] =
    field[Int]("current")
      .zip(field[Int]("pageCount"))
      .zip(field[Long]("totalSize"))
      .xmap[Pagination] { case (current, pageCount, totalSize) => Pagination(current, pageCount, totalSize) } {
        case Pagination(current, pageCount, totalSize) => (current, pageCount, totalSize)
      }

  implicit def pageSchema[A: JsonSchema]: JsonSchema[Page[A]] =
    field[Pagination]("pagination")
      .zip(field[Seq[A]]("items"))
      .xmap[Page[A]] { case (pagination, items) => Page(pagination, items) } {
        case Page(pagination, items) => (pagination, items)
      }
}
