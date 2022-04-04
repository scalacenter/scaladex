package scaladex.core.api

import endpoints4s.algebra.JsonEntitiesFromSchemas
import scaladex.core.model.search.Pagination

trait PaginationSchema extends JsonEntitiesFromSchemas {

  implicit val paginationSchema: JsonSchema[Pagination] =
    field[Int]("current")
      .zip(field[Int]("pageCount"))
      .zip(field[Long]("totalSize"))
      .xmap[Pagination] { case (current, pageCount, totalSize) => Pagination(current, pageCount, totalSize) } {
        pagination => (pagination.current, pagination.pageCount, pagination.totalSize)
      }

}
