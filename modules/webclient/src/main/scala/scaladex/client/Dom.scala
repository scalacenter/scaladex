package scaladex.client

import org.scalajs.dom.Element
import org.scalajs.dom.HTMLInputElement
import org.scalajs.dom.Node
import org.scalajs.dom.document
import scaladex.core.api.AutocompletionParams

object Dom {
  def getSearchRequest: Option[AutocompletionParams] =
    for (query <- getSearchQuery)
      yield AutocompletionParams(
        query = query,
        you = getById[HTMLInputElement]("you").map(_.value).contains("✓"),
        topics = getSearchFilter("topics"),
        languages = getSearchFilter("languages"),
        platforms = getSearchFilter("platforms"),
        contributingSearch = getById[HTMLInputElement]("contributing-search").map(_.value).contains("true")
      )

  def getSearchQuery: Option[String] =
    getSearchInput.map(_.value).filter(_.length > 0)

  def getSearchInput: Option[HTMLInputElement] = getById("search")

  def getResultList: Option[Element] = getById("list-result")

  def getById[E <: Element](id: String): Option[E] =
    Option(document.getElementById(id)).map(_.asInstanceOf[E])

  def getBySelectors[E <: Element](selectors: String): Option[E] =
    Option(document.querySelector(selectors)).map(_.asInstanceOf[E])

  def getSearchFilter(name: String): Seq[String] =
    getAllByName[HTMLInputElement](name).filter(_.checked).map(_.value)

  def getAllByName[E <: Node](name: String): Seq[E] =
    document.getElementsByName(name).toSeq.map(_.asInstanceOf[E])

  def getAllByClassNames[E <: Element](classNames: String): Seq[E] =
    document.getElementsByClassName(classNames).toSeq.map(_.asInstanceOf[E])

  def getAllBySelectors[E <: Element](selectors: String): Seq[E] =
    document.querySelectorAll(selectors).toSeq.map(_.asInstanceOf[E])
}
