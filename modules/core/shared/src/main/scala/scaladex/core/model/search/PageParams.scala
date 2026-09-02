package scaladex.core.model.search

final case class PageParams(page: Int, size: Int)

object PageParams:
  val MaxSize: Int = 100

  /** Builds [[PageParams]] with `page` forced to at least 1 and `size` clamped to `[1, MaxSize]`, so that callers
    * cannot trigger negative/huge SQL `LIMIT`/`OFFSET` or nonsensical pagination metadata.
    */
  def bounded(page: Int, size: Int): PageParams =
    PageParams(page.max(1), size.max(1).min(MaxSize))
end PageParams
