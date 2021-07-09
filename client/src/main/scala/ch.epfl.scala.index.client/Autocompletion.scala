package ch.epfl.scala.index.client

import scala.concurrent.ExecutionContext

import ch.epfl.scala.index.api.AutocompletionResponse
import ch.epfl.scala.index.client.rpc.RPC
import org.scalajs.dom
import org.scalajs.dom.KeyboardEvent
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.Element
import org.scalajs.dom.raw.HTMLUListElement
import org.scalajs.dom.raw.Node
import org.scalajs.jquery.jQuery
import scalatags.JsDom.all._

class Autocompletion(implicit ec: ExecutionContext) {
  case class CompletionSelection(
      selected: Option[Int],
      choices: List[AutocompletionResponse]
  )

  object CompletionSelection {
    val empty: CompletionSelection = CompletionSelection(None, Nil)
  }

  private var completionSelection = CompletionSelection.empty

  def run(event: dom.Event): Unit = {
    Dom.getSearchRequest match {
      case Some(request) =>
        for (autocompletion <- RPC.autocomplete(request))
          yield update(autocompletion, request.query)
      case None => cleanResults()
    }
  }

  def navigate(event: KeyboardEvent): Unit = {
    if (event.keyCode == KeyCode.Up && completionSelection.choices.nonEmpty) {
      moveSelection(
        completionSelection.selected.map(_ - 1).filter(_ >= 0)
      )
    } else if (
      event.keyCode == KeyCode.Down && completionSelection.choices.nonEmpty
    ) {
      moveSelection(
        completionSelection.selected.fold[Option[Int]](Some(0))(i =>
          Some(math.min(i + 1, completionSelection.choices.size - 1))
        )
      )
    } else if (event.keyCode == KeyCode.Enter) {
      completionSelection.selected.foreach { selected =>
        event.preventDefault()
        val AutocompletionResponse(owner, repo, _) =
          completionSelection.choices(selected)
        dom.window.location.assign(s"/$owner/$repo")
      }
    } else if (event.keyCode == KeyCode.Escape) {
      cleanResults()
    } else ()

    def moveSelection(newSelected: Option[Int]): Unit = {
      event.preventDefault()
      completionSelection = completionSelection.copy(selected = newSelected)
      updateSelection()
    }

    def updateSelection(): Unit = {
      Dom.getResultList.foreach { resultList =>
        for (i <- 0 until resultList.childElementCount) {
          val resultElement =
            resultList.childNodes(i).asInstanceOf[HTMLUListElement]
          if (completionSelection.selected.contains(i)) {
            resultElement.classList.add("selected")
          } else {
            resultElement.classList.remove("selected")
          }
        }
      }
    }
  }

  private def cleanResults(): Unit = {
    completionSelection = CompletionSelection.empty
    Dom.getResultList.fold(())(_.innerHTML = "")
  }

  private def update(
      projects: List[AutocompletionResponse],
      query: String
  ): Unit = {
    if (Dom.getSearchQuery.contains(query)) {
      cleanResults()
      completionSelection = CompletionSelection(None, projects)
      projects.map {
        case AutocompletionResponse(organization, repository, description) =>
          appendResult(
            organization,
            repository,
            description
          )
      }
    }
  }

  private def appendResult(
      owner: String,
      repo: String,
      description: String
  ): Option[Node] = {
    for {
      resultContainer <- Dom.getResultList
      newItem = projectSuggestion(owner, repo, description)
    } yield {
      jQuery(newItem).find(".emojify").each { el: Element =>
        emojify.run(el)
      }
      resultContainer.appendChild(newItem)
    }
  }

  private def projectSuggestion(
      owner: String,
      repo: String,
      description: String
  ): Element = {
    li(
      a(href := s"/$owner/$repo")(
        p(s"$owner / $repo"),
        span(cls := "emojify")(description)
      )
    ).render
  }

}
