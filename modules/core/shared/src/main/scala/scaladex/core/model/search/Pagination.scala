package scaladex.core.model.search

/**
 * Pagination info
 *
 * @param current   page
 * @param pageCount number of pages at all
 * @param pageSize number of results at all
 */
case class Pagination(current: Int, pageCount: Int, pageSize: Long)
