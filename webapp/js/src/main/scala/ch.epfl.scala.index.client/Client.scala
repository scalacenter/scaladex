package ch.epfl.scala.index
package client

import api.Api
import client.rpc.AutowireClient

import autowire._

import scalatags.JsDom.all._

import org.scalajs.dom.document
import scala.scalajs.js.annotation.JSExport
import org.scalajs.dom.raw.{Node, HTMLInputElement, Element}

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.concurrent.Future
import scala.util.Try

@JSExport
object Client {

  val searchId = "search"
  val resultElementId = "list-result"

  def getResultList: Option[Element] = getElement(resultElementId)

  def getSearchInput: Option[HTMLInputElement] = getElement(searchId).map(_.getInput)

  def getElement(id: String) : Option[Element] = Try(document.getElementById(id)).toOption

  def appendResult(owner: String, repo: String, description: String): Option[Node] = for {
    resultContainer <- getResultList
    newItem = newProjectItem(owner, repo, description)
  } yield resultContainer.appendChild(newItem)


  def newProjectItem(owner: String, repo: String, description: String): Element = li(
    a(href := s"/$owner/$repo")(
      p(s"$owner / $repo"),
      span(description)
    )
  ).render

  def getQuery(input: Option[HTMLInputElement]): Option[String] = input match {
    case Some(i) if i.value.length > 1 => Option(i.value)
    case _ => None
  }


  def getProjects(query: String) = AutowireClient[Api].autocomplete(query).call()

  def showResults(projects: List[(String, String, String)]): List[Option[Node]] = 
    projects.map{ case (owner, artifact, description) =>
      appendResult(
        owner,
        artifact,
        description
      )
    }

  def cleanResults(): Unit = getResultList.fold()(_.innerHTML = "")

  @JSExport
  def runSearch(): Future[List[Option[Node]]] = {
    cleanResults()
    getQuery(getSearchInput).fold(
      Future.successful(List.empty[(String, String, String)])
    )(getProjects).map(showResults)
  }

  implicit class ElementOps(e: Element) {
    def getInput: HTMLInputElement = get[HTMLInputElement]
    def get[A <: Element]: A = e.asInstanceOf[A]
  }
}