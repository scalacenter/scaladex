package scaladex.core.model.search

final case class PageParams(page: Int, size: Int)

object PageParams:
  // A small, fixed set of page sizes keeps the REST API cache-friendly under load.
  val AllowedSizes: Seq[Int] = Seq(20, 50, 100)
  val DefaultSize: Int = AllowedSizes.head
end PageParams
