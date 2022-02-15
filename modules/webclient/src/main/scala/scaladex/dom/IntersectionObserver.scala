package scaladex.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

import org.scalajs.dom.Element

@js.native // https://developer.mozilla.org/en-US/docs/Web/API/IntersectionObserver
@JSGlobal
class IntersectionObserver(callback: js.Function1[js.Array[IntersectionObserverEntry], Unit]) extends js.Object {
  def observe(element: Element): Unit = js.native
}

object IntersectionObserver {
  def apply(callback: IntersectionObserverEntry => Unit): IntersectionObserver =
    new IntersectionObserver(entries => entries.foreach(callback))
}
