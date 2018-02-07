package ch.epfl.scala.index
package client

import autowire._
import api._
import rpc.AutowireClient
import org.scalajs.dom
import org.scalajs.dom.ext.{Ajax, KeyCode}
import org.scalajs.dom.{Event, KeyboardEvent, document}
import org.scalajs.dom.raw._
import org.scalajs.jquery.jQuery

import scalatags.JsDom.all._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scalajs.js.JSApp
import scala.scalajs.js.UndefOr
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.concurrent.Future
import scala.util.Try
@JSExportTopLevel("ch.epfl.scala.index.client.Client")
object Client {

  private val searchId = "search"
  private val resultElementId = "list-result"
  private var completionSelection = CompletionSelection.empty

  // used to access methods from bootstrap-select library like .selectpicker("val")
  // see https://silviomoreto.github.io/bootstrap-select/methods/
  private def getIssuesSelect =
    jQuery("#selectedBeginnerIssues").asInstanceOf[js.Dynamic]

  private def getResultList: Option[Element] = getElement(resultElementId)

  private def getSearchBox: Option[Element] =
    getElement(searchId)

  private def getSearchInput: Option[HTMLInputElement] =
    getSearchBox.map(_.getInput)

  private def getElement(id: String): Option[Element] =
    Option(document.getElementById(id))

  private def appendResult(owner: String,
                           repo: String,
                           description: String): Option[Node] = {
    for {
      resultContainer <- getResultList
      newItem = newProjectItem(owner, repo, description)
    } yield resultContainer.appendChild(newItem)
  }

  private def newProjectItem(owner: String,
                             repo: String,
                             description: String): Element = {
    li(
      a(href := s"/$owner/$repo")(
        p(s"$owner / $repo"),
        span(description)
      )
    ).render
  }

  private def getQuery(input: Option[HTMLInputElement]): Option[String] =
    input match {
      case Some(i) if i.value.length > 1 => Option(i.value)
      case _                             => None
    }

  private def getProjects(query: String): Future[List[Autocompletion]] =
    AutowireClient[Api].autocomplete(query).call()

  private def showResults(projects: List[Autocompletion]): List[Option[Node]] = {
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

  private def cleanResults(): Unit = {
    completionSelection = CompletionSelection.empty
    getResultList.fold(())(_.innerHTML = "")
  }

  private def runSearch(event: dom.Event): Future[List[Option[Node]]] = {
    cleanResults()
    getQuery(getSearchInput)
      .fold(
        Future.successful(List.empty[Autocompletion])
      )(getProjects)
      .map(showResults)
  }

  private def navigate(event: KeyboardEvent): Unit = {
    if (event.keyCode == KeyCode.Up && completionSelection.choices.nonEmpty) {
      moveSelection(
        completionSelection.selected.map(_ - 1).filter(_ >= 0)
      )
    } else if (event.keyCode == KeyCode.Down && completionSelection.choices.nonEmpty) {
      moveSelection(
        completionSelection.selected.fold[Option[Int]](Some(0))(
          i => Some(math.min(i + 1, completionSelection.choices.size - 1))
        )
      )
    } else if (event.keyCode == KeyCode.Enter) {
      completionSelection.selected.foreach { selected =>
        event.preventDefault()
        val Autocompletion(owner, repo, _) =
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
      getResultList.foreach { resultList =>
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

  private def jumpToSearchInput(event: KeyboardEvent): Unit = {
    if (event.keyCode == KeyCode.S) {
      getSearchBox.foreach { searchBox =>
        val input = searchBox.getInput
        if (event.target != input) {
          input.focus()
          event.preventDefault()
        }
      }
    }
  }

  implicit class ElementOps(e: Element) {
    def getInput: HTMLInputElement = get[HTMLInputElement]
    def get[A <: Element]: A = e.asInstanceOf[A]
  }

  case class CompletionSelection(selected: Option[Int],
                                 choices: List[Autocompletion])

  object CompletionSelection {
    val empty = CompletionSelection(None, Nil)
  }

  private def fetchAndReplaceReadme(el: Element, token: Option[String]): Unit = {

    val organization = el.attributes.getNamedItem("data-organization").value
    val repository = el.attributes.getNamedItem("data-repository").value

    val headers = Map(
      "Accept" -> "application/vnd.github.VERSION.html"
    )

    val headersWithCreds =
      token
        .map(t => headers + ("Authorization" -> s"bearer $t"))
        .getOrElse(headers)

    Ajax
      .get(
        url = s"https://api.github.com/repos/$organization/$repository/readme",
        data = "",
        timeout = 0,
        headers = headersWithCreds
      )
      .foreach { xhr =>
        el.innerHTML = xhr.responseText
      }
  }

  @js.native
  trait Issue extends js.Object {
    val html_url: String = js.native
    val number: Int = js.native
    val title: String = js.native
  }

  private def getIssueJson(issue: Issue): String = {
    s"""{ "number":${issue.number}, "title":"${issue.title
      .replace("\"", "\\\"")}", "url":{"target":"${issue.html_url}"} }"""
  }

  private def getIssuesListener(
      token: Option[String]
  )(event: dom.Event): Unit = {
    getIssues(token)
  }

  private def getIssues(token: Option[String],
                        showSelected: Boolean = false): Unit = {
    getElement("beginnerIssuesLabel").foreach { issuesLabelEl =>
      val label = issuesLabelEl.getInput.value
      if (!label.isEmpty) {
        val organization =
          issuesLabelEl.attributes.getNamedItem("data-organization").value
        val repository =
          issuesLabelEl.attributes.getNamedItem("data-repository").value

        val headers = Map(
          "Accept" -> "application/vnd.github.VERSION.raw+json"
        )

        val headersWithCreds =
          token
            .map(t => headers + ("Authorization" -> s"bearer $t"))
            .getOrElse(headers)

        Ajax
          .get(
            url =
              s"https://api.github.com/repos/$organization/$repository/issues?state=open&labels=$label",
            data = "",
            timeout = 0,
            headers = headersWithCreds
          )
          .foreach { xhr =>
            val rawIssues = js.JSON.parse(xhr.responseText)
            val issues = rawIssues.asInstanceOf[js.Array[Issue]]
            getElement("selectedBeginnerIssues").foreach { el =>
              val options = issues.map { issue =>
                s"""<option value='${getIssueJson(issue)}' title="#${issue.number}"> #${issue.number} - ${issue.title}</option>"""
              }
              val selectEl = el.asInstanceOf[HTMLSelectElement]
              selectEl.innerHTML = options.mkString
              getElement("beginnerIssues").foreach { beginnerIssuesEl =>
                val beginnerIssuesJson =
                  s"[${issues.map(getIssueJson).mkString(",")}]"
                beginnerIssuesEl.getInput.value = beginnerIssuesJson
              }

              disableBeginnerIssues(false)

              if (showSelected) {
                val selectedIssueNumbers: Array[Int] =
                  if (!el.getAttribute("data-selected").isEmpty)
                    el.getAttribute("data-selected").split(",").map(_.toInt)
                  else Array()
                val selectedIssueValues = issues.collect {
                  case issue if selectedIssueNumbers.contains(issue.number) =>
                    getIssueJson(issue)
                }
                getIssuesSelect.selectpicker("val",
                                             selectedIssueValues.toJSArray)
              }
            }
          }
      } else {
        getElement("selectedBeginnerIssues").foreach { el =>
          val selectEl = el.asInstanceOf[HTMLSelectElement]
          selectEl.innerHTML = ""
          getElement("beginnerIssues").foreach { beginnerIssuesEl =>
            beginnerIssuesEl.getInput.value = ""
          }
          disableBeginnerIssues(true)
        }
      }
    }
  }

  private def disableBeginnerIssues(disable: Boolean): Unit = {
    jQuery("#selectedBeginnerIssues").prop("disabled", disable)
    getIssuesSelect.selectpicker("refresh")
    getIssuesSelect.selectpicker("deselectAll")
  }

  private def hideBanner(event: dom.Event) = {
    jQuery(".banner").hide()
  }

  @JSExport
  def main(token: UndefOr[String]): Unit = {
    document.addEventListener[KeyboardEvent]("keydown", jumpToSearchInput _)

    getSearchBox.foreach { searchBox =>
      searchBox.addEventListener[Event]("input", runSearch _)
      searchBox.addEventListener[KeyboardEvent]("keydown", navigate _)
    }

    getElement("README").foreach { readmeEl =>
      fetchAndReplaceReadme(readmeEl, token.toOption)
    }

    getElement("beginnerIssuesLabel").foreach { el =>
      el.addEventListener[Event]("change", getIssuesListener(token.toOption) _)
    }

    getIssues(token.toOption, true)

    getElement("hide-banner").foreach { el =>
      el.addEventListener[Event]("click", hideBanner _)
    }

  }
}
