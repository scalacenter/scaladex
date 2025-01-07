package scaladex.dom

import scala.scalajs.js

import org.scalajs.dom.html.Element

@js.native // https://developer.mozilla.org/en-US/docs/Web/API/IntersectionObserverEntry
trait IntersectionObserverEntry extends js.Object:
  def intersectionRatio: Double = js.native
  def target: Element = js.native
