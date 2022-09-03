package scaladex.client

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.UndefOr
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.KeyboardEvent
import org.scalajs.dom.document
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw._
import org.scalajs.jquery.jQuery

@JSExportTopLevel("ScaladexClient")
object Client {
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  // used to access methods from bootstrap-select library like .selectpicker("val")
  // see https://silviomoreto.github.io/bootstrap-select/methods/
  private def getIssuesSelect =
    jQuery("#selectedBeginnerIssues").asInstanceOf[js.Dynamic]

  private def jumpToSearchInput(event: KeyboardEvent): Unit =
    if (event.ctrlKey && event.keyCode == KeyCode.S) {
      Dom.getSearchInput.foreach { input =>
        if (event.target != input) {
          input.focus()
          event.preventDefault()
        }
      }
    }

  private def fetchAndReplaceReadme(el: Element, token: Option[String]): Unit = {
    val organization = el.attributes.getNamedItem("data-organization").value
    val repository = el.attributes.getNamedItem("data-repository").value
    val headers = Map("Accept" -> "application/vnd.github.VERSION.html")

    val headersWithCreds =
      token
        .map(t => headers + ("Authorization" -> s"bearer $t"))
        .getOrElse(headers)

    val root = s"https://github.com/$organization/$repository"
    def base(v: String) = s"$root/$v/master"
    val raw = base("raw")
    val blob = base("blob")

    Ajax
      .get(
        url = s"https://api.github.com/repos/$organization/$repository/readme",
        data = "",
        timeout = 0,
        headers = headersWithCreds
      )
      .foreach { xhr =>
        el.innerHTML = xhr.responseText

        jQuery(el)
          .find("img, a")
          .not("[href^='http'],[href^='https'],[src^='http'],[src^='https']")
          .each { e: Element =>
            val (at, newBase) = if (e.tagName == "A") {
              val attr = "href"
              val href =
                if (e.getAttribute(attr).startsWith("#")) root
                else blob

              e.setAttribute("target", "_blank")
              (attr, href)
            } else ("src", raw)

            Option(e.getAttribute(at))
              .foreach { oldUrl =>
                if (oldUrl.nonEmpty) {
                  val newUrl =
                    if (!oldUrl.startsWith("/")) s"$newBase/$oldUrl"
                    else s"$newBase$oldUrl"
                  e.setAttribute(at, newUrl)
                }
              }
          }
      }
  }

  @js.native
  trait Issue extends js.Object {
    val html_url: String = js.native
    val number: Int = js.native
    val title: String = js.native
  }

  private def getIssueJson(issue: Issue): String =
    s"""{ "number":${issue.number}, "title":"${issue.title
        .replace("\"", "\\\"")}", "url":{"target":"${issue.html_url}"} }"""

  private def getIssuesListener(token: Option[String])(event: dom.Event): Unit =
    getIssues(token)

  private def getIssues(token: Option[String], showSelected: Boolean = false): Unit = {
    import Dom.ElementOps
    Dom.getElementById("beginnerIssuesLabel").foreach { issuesLabelEl =>
      val label = issuesLabelEl.asInput.value
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
            url = s"https://api.github.com/repos/$organization/$repository/issues?state=open&labels=$label",
            data = "",
            timeout = 0,
            headers = headersWithCreds
          )
          .foreach { xhr =>
            val rawIssues = js.JSON.parse(xhr.responseText)
            val issues = rawIssues.asInstanceOf[js.Array[Issue]]
            Dom.getElementById("selectedBeginnerIssues").foreach { el =>
              val options = issues.map { issue =>
                s"""<option value='${getIssueJson(
                    issue
                  )}' title="#${issue.number}"> #${issue.number} - ${issue.title}</option>"""
              }
              val selectEl = el.asInstanceOf[HTMLSelectElement]
              selectEl.innerHTML = options.mkString
              Dom.getElementById("openIssues").foreach { openIssuesEl =>
                val openIssuesJson =
                  s"[${issues.map(getIssueJson).mkString(",")}]"
                openIssuesEl.asInput.value = openIssuesJson
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
                getIssuesSelect
                  .selectpicker("val", selectedIssueValues.toJSArray)
              }
            }
          }
      } else {
        Dom.getElementById("selectedBeginnerIssues").foreach { el =>
          val selectEl = el.asInstanceOf[HTMLSelectElement]
          selectEl.innerHTML = ""
          Dom.getElementById("openIssues").foreach(openIssuesEl => openIssuesEl.asInput.value = "")
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

  private def hideBanner(event: dom.Event) =
    jQuery(".banner").hide()

  @JSExport
  def main(token: UndefOr[String]): Unit = {
    document.addEventListener[KeyboardEvent]("keydown", jumpToSearchInput _)

    val autocompletion = new Autocompletion()

    Dom.getSearchBox.foreach { searchBox =>
      searchBox.addEventListener[Event]("input", autocompletion.run _)
      searchBox
        .addEventListener[KeyboardEvent]("keydown", autocompletion.navigate _)
    }

    Dom.getElementById("README").foreach(readmeEl => fetchAndReplaceReadme(readmeEl, token.toOption))

    Dom.getElementById("beginnerIssuesLabel").foreach { el =>
      el.addEventListener[Event]("change", getIssuesListener(token.toOption) _)
    }

    getIssues(token.toOption, showSelected = true)

    Dom.getElementById("hide-banner").foreach(el => el.addEventListener[Event]("click", hideBanner _))

    emojify.setConfig(
      js.Dictionary(
        "img_dir" -> "https://cdnjs.cloudflare.com/ajax/libs/emojify.js/1.1.0/images/basic"
      )
    )
    jQuery(".emojify").each { el: Element => emojify.run(el) }
    emojify.run(document.body)

    CopyToClipboard.addCopyListenersOnClass("btn-copy")

    ActiveNavObserver.start()

  }

  @JSExport
  def createSparkline(): Unit =
    Sparkline.createCommitActivitySparkline()

  @JSExport
  def updateVisibleArtifactsInGrid(): Unit = {
    def valuesOfCheckedInputsWithName(name: String): Set[String] =
      jQuery(s"input[name='$name']")
        .toArray()
        .asInstanceOf[js.Array[HTMLInputElement]]
        .filter(_.checked)
        .map(_.value)
        .toSet

    val selectedPlatforms = valuesOfCheckedInputsWithName("platform")
    val selectedBinaryVersions = valuesOfCheckedInputsWithName("binary-version")
    val allRequiredClasses = selectedPlatforms ++ selectedBinaryVersions

    jQuery(".artifact-line").each { (_, elem) =>
      val supported = allRequiredClasses.forall(elem.classList.contains(_))

      if (supported) {
        elem.classList.remove("artifact-line-hidden")
        elem.classList.add("artifact-line-visible")
      } else {
        elem.classList.remove("artifact-line-visible")
        elem.classList.add("artifact-line-hidden")
      }
    }

    jQuery(".version-line").each { (_, elem) =>
      val supportedCount =
        elem.querySelectorAll("tr.artifact-line-visible").length

      val versionCell =
        elem.querySelector("td.version").asInstanceOf[HTMLTableCellElement]
      versionCell.rowSpan = supportedCount + 1

      if (supportedCount != 0) {
        elem.classList.remove("version-line-hidden")
        elem.classList.add("version-line-visible")
      } else {
        elem.classList.remove("version-line-visible")
        elem.classList.add("version-line-hidden")
      }
    }
  }

}
