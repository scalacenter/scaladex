package scaladex.core.model.search

case class Page[A](pagination: Pagination, items: Seq[A]) {
  def map[B](f: A => B): Page[B] = Page(pagination, items.map(f))

  def flatMap[B](f: A => Iterable[B]): Page[B] = Page(pagination, items.flatMap(f))
}

object Page {

  def empty[A]: Page[A] =
    Page(
      pagination = Pagination(current = 1, pageCount = 1, totalSize = 0),
      items = Seq.empty
    )
}
