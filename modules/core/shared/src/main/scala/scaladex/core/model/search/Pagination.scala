package scaladex.core.model.search

/**
 * Pagination info
 *
 * @param current   page
 * @param pageCount number of pages
 * @param totalSize total number of results
 */
case class Pagination(current: Int, pageCount: Int, totalSize: Long)
