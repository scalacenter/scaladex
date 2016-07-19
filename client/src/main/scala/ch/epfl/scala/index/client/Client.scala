package ch.epfl.scala.index.client

import autowire._
import ch.epfl.scala.index.api.Api
import ch.epfl.scala.index.api.Api.Autocompletion
import ch.epfl.scala.index.client.rpc.AutowireClient
import org.scalajs.dom

import scalatags.JsDom.all._
import org.scalajs.dom.{Event, document}

import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom.raw.{Element, HTMLInputElement, Node}

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.concurrent.Future
import scala.scalajs.js.JSApp
import scala.util.Try

trait ClientBase {

  val searchId = "search"
  val resultElementId = "list-result"

  def getResultList: Option[Element] = getElement(resultElementId)

  def getSearchBox: Option[Element] =
    getElement(searchId)

  def getSearchInput: Option[HTMLInputElement] =
    getSearchBox.map(_.getInput)

  def getElement(id: String): Option[Element] =
    Try(document.getElementById(id)).toOption

  def appendResult(
      owner: String, repo: String, description: String): Option[Node] = {
    for {
      resultContainer <- getResultList
      newItem = newProjectItem(owner, repo, description)
    } yield resultContainer.appendChild(newItem)
  }

  def newProjectItem(
      owner: String, repo: String, description: String): Element = {
    li(
      a(href := s"/$owner/$repo")(
          p(s"$owner / $repo"),
          span(description)
      )
    ).render
  }

  def getQuery(input: Option[HTMLInputElement]): Option[String] = input match {
    case Some(i) if i.value.length > 1 => Option(i.value)
    case _ => None
  }

  def getProjects(query: String) =
    AutowireClient[Api].search(query).call()

  def showResults(
      projects: List[Autocompletion]): List[Option[Node]] =
    projects.map {
      case result =>
        val (ref, description) = result
        appendResult(
          ref.organization,
          ref.repository,
          description
        )
    }

  def cleanResults(): Unit = getResultList.fold()(_.innerHTML = "")

  @JSExport
  def runSearch(event: dom.Event): Future[List[Option[Node]]] = {
    cleanResults()
    getQuery(getSearchInput)
      .fold(
          Future.successful(List.empty[Autocompletion])
      )(getProjects)
      .map(showResults)
  }

  implicit class ElementOps(e: Element) {
    def getInput: HTMLInputElement = get[HTMLInputElement]
    def get[A <: Element]: A = e.asInstanceOf[A]
  }
}

object Client extends JSApp with ClientBase {

  override def main(): Unit = {
    getSearchBox.foreach(_.addEventListener[Event]("input", runSearch _))
  }

}

