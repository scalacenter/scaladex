package ch.epfl.scala.index.client

import org.scalajs.dom.document
import org.scalajs.dom.raw.{Element, HTMLInputElement}

object Dom {
  private val searchId = "search"
  private val resultElementId = "list-result"

  def getSearchQuery: Option[String] = getSearchInput.map(_.value)

  def getSearchInput: Option[HTMLInputElement] = getSearchBox.map(_.asInput)

  def getResultList: Option[Element] = getElement(resultElementId)

  def getSearchBox: Option[Element] = getElement(searchId)

  def getElement(id: String): Option[Element] =
    Option(document.getElementById(id))

  implicit class ElementOps(e: Element) {
    def asInput: HTMLInputElement = as[HTMLInputElement]
    def as[A <: Element]: A = e.asInstanceOf[A]
  }
}
