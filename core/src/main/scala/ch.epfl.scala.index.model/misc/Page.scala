package ch.epfl.scala.index.model.misc

case class Page[A](pagination: Pagination, items: Seq[A])
