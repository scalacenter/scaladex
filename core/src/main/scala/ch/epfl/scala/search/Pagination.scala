package ch.epfl.scala.search

/**
 * Pagination info
 * @param current page
 * @param pageCount number of pages at all
 * @param itemCount number of results at all
 */
case class Pagination(current: Int, pageCount: Int, itemCount: Long)
