package scaladex.core.util

object Ordering {
  def Descending[T: Ordering]: Ordering[T] = implicitly[Ordering[T]].reverse
}
