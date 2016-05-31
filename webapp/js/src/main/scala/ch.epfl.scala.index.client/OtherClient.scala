package ch.epfl.scala.index.client

import ch.epfl.scala.index.api.Api
import ch.epfl.scala.index.client.rpc.AutowireClient
import ch.epfl.scala.index.model.Project
import org.scalajs.dom.raw.{Node, HTMLInputElement, Element}
import scala.concurrent.Future
import scala.util.Try
import scalatags.JsDom.all._
import org.scalajs.dom.document
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import autowire._
import scalajs.concurrent.JSExecutionContext.Implicits.queue



object OtherClient extends JSApp {

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
    a(href := s"/poc/$owner/$repo")(
      p(s"$owner / $repo"),
      span(description)
    )
  ).render

  def getQuery(input: Option[HTMLInputElement]): Option[String] = input match {
    case Some(i) if i.value.length > 1 => Option(i.value)
    case _ => None
  }


  def getProjects(query: String): Future[List[Project]] = AutowireClient[Api].find(query, page = 0).call().map{
    case (pagination, projects) => projects
    case _ => List.empty[Project]
  }

  def showResults(projects: List[Project]): List[Option[Node]] = projects map(p => appendResult(
    owner = p.reference.organization,
    repo = p.reference.repository,
    description = p.artifacts.head.releases.head.description.getOrElse("")))


  def cleanResults(): Unit = getResultList.fold()(_.innerHTML = "")


  @JSExport
  override def main(): Unit = {
    ()
  }

  @JSExport
  def runSearch(): Future[List[Option[Node]]] = {
    cleanResults()
    getQuery(getSearchInput).fold(Future.successful(List.empty[Project]))(getProjects).map(showResults)
  }


  implicit class ElementOps(e: Element) {

    def getInput: HTMLInputElement = get[HTMLInputElement]

    def get[A <: Element]: A = e.asInstanceOf[A]
  }



}