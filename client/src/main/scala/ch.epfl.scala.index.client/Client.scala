package ch.epfl.scala.index
package client

import autowire._
import api._
import rpc.AutowireClient
import org.scalajs.dom
import org.scalajs.dom.{Event, KeyboardEvent, document}
import org.scalajs.dom.raw.{Element, HTMLInputElement, HTMLUListElement, Node}

import scalatags.JsDom.all._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.annotation.JSExport
import scalajs.js.JSApp
import scala.concurrent.Future
import scala.util.Try

trait ClientBase {

  val searchId        = "search"
  val resultElementId = "list-result"
  var completionSelection: CompletionSelection = CompletionSelection.empty

  def getResultList: Option[Element] = getElement(resultElementId)

  def getSearchBox: Option[Element] =
    getElement(searchId)

  def getSearchInput: Option[HTMLInputElement] =
    getSearchBox.map(_.getInput)

  def getElement(id: String): Option[Element] =
    Try(document.getElementById(id)).toOption

  def appendResult(owner: String,
                   repo: String,
                   description: String): Option[Node] = {
    for {
      resultContainer <- getResultList
      newItem = newProjectItem(owner, repo, description)
    } yield resultContainer.appendChild(newItem)
  }

  def newProjectItem(owner: String,
                     repo: String,
                     description: String): Element = {
    li(
        a(href := s"/$owner/$repo")(
            p(s"$owner / $repo"),
            span(description)
        )
    ).render
  }

  def getQuery(input: Option[HTMLInputElement]): Option[String] = input match {
    case Some(i) if i.value.length > 1 => Option(i.value)
    case _                             => None
  }

  def getProjects(query: String): Future[List[Autocompletion]] =
    AutowireClient[Api].search(query).call()

  def showResults(projects: List[Autocompletion]): List[Option[Node]] = {
    completionSelection = CompletionSelection(None, projects)
    projects.map {
      case Autocompletion(organization, repository, description) =>
        appendResult(
          organization,
          repository,
          description
        )
    }
  }

  def cleanResults(): Unit = {
    completionSelection = CompletionSelection.empty
    getResultList.fold()(_.innerHTML = "")
  }

  @JSExport
  def runSearch(event: dom.Event): Future[List[Option[Node]]] = {
    cleanResults()
    getQuery(getSearchInput)
      .fold(
          Future.successful(List.empty[Autocompletion])
      )(getProjects)
      .map(showResults)
  }

  def navigate(event: KeyboardEvent): Unit = {
    if (event.keyCode == 38 /* Up */ && completionSelection.choices.nonEmpty) {
      moveSelection(
        completionSelection.selected.map(_ - 1).filter(_ >= 0)
      )
    } else if (event.keyCode == 40 /* Down */ && completionSelection.choices.nonEmpty) {
      moveSelection(
        completionSelection.selected
          .fold[Option[Int]](Some(0))(i => Some(math.min(i + 1, completionSelection.choices.size - 1)))
      )
    } else if (event.keyCode == 13 /* Enter */) {
      completionSelection.selected.foreach { selected =>
        event.preventDefault()
        val Autocompletion(owner, repo, _) = completionSelection.choices(selected)
        dom.window.location.assign(s"/$owner/$repo")
      }
    } else if (event.keyCode == 27 /* Esc */) {
      cleanResults()
    } else ()

    def moveSelection(newSelected: Option[Int]): Unit = {
      event.preventDefault()
      completionSelection = completionSelection.copy(selected = newSelected)
      updateSelection()
    }

    def updateSelection(): Unit = {
      getResultList.foreach { resultList =>
        for (i <- 0 until resultList.childElementCount) {
          val resultElement = resultList.childNodes(i).asInstanceOf[HTMLUListElement]
          if (completionSelection.selected.contains(i)) {
            resultElement.classList.add("selected")
          } else {
            resultElement.classList.remove("selected")
          }
        }
      }
    }
  }

  implicit class ElementOps(e: Element) {
    def getInput: HTMLInputElement = get[HTMLInputElement]
    def get[A <: Element]: A       = e.asInstanceOf[A]
  }

  case class CompletionSelection(selected: Option[Int], choices: List[Autocompletion])

  object CompletionSelection {
    val empty = CompletionSelection(None, Nil)
  }

}

object Client extends JSApp with ClientBase {

  override def main(): Unit = {
    getSearchBox.foreach { searchBox =>
      searchBox.addEventListener[Event]("input", runSearch _)
      searchBox.addEventListener[KeyboardEvent]("keydown", navigate _)
    }
  }

}
