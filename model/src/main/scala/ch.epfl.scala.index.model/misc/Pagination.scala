package ch.epfl.scala.index.model
package misc

/**
 * Pagination helper model
 * @param current page
 * @param pageCount number of pages at all
 * @param itemCount number of results at all
 */
case class Pagination(current: PageIndex, pageCount: Int, itemCount: Long)
