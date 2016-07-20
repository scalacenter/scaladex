package ch.epfl.scala.index.model
package misc

/**
  * Pagination helper model
  * @param current page
  * @param totalPages number of pages at all
  * @param total number of results at all
  */
case class Pagination(current: PageIndex, totalPages: Int, total: Long)
