package scaladex.client

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.UndefOr
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

import org.scalajs.dom.*
import org.scalajs.dom.document

@JSExportTopLevel("ScaladexClient")
object Client:
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  private def jumpToSearchInput(event: KeyboardEvent): Unit =
    if event.ctrlKey && event.keyCode == KeyCode.S then
      Dom.getSearchInput.foreach { input =>
        if event.target != input then
          input.focus()
          event.preventDefault()
      }

  @js.native
  trait Repo extends js.Object:
    val default_branch: String = js.native

  private[client] def resolveReadmeLinkUrl(
      isAnchor: Boolean,
      attrValue: String,
      root: String,
      raw: String,
      blob: String
  ): (String, String) =
    val newBase =
      if isAnchor then if attrValue.startsWith("#") then root else blob
      else raw
    val newUrl =
      if !attrValue.startsWith("/") then s"$newBase/$attrValue"
      else s"$newBase$attrValue"
    (if isAnchor then "href" else "src", newUrl)
  end resolveReadmeLinkUrl

  // The readme is already rendered server-side (fetched with an authenticated request during indexing);
  // here we only rewrite its relative links/images to point at GitHub, without re-fetching or replacing
  // the readme content itself (an unauthenticated client-side re-fetch is prone to GitHub API rate limits).
  private def fixReadmeLinks(element: Element, token: Option[String]): Unit =

    val organization = element.attributes.getNamedItem("data-organization").value
    val repository = element.attributes.getNamedItem("data-repository").value
    val headers = Map("Accept" -> "application/vnd.github.VERSION.html")

    val headersWithCreds =
      token
        .map(t => headers + ("Authorization" -> s"bearer $t"))
        .getOrElse(headers)

    def extractDefaultBranch(text: String): String =
      js.JSON.parse(text).asInstanceOf[Repo].default_branch

    def fixImages(branch: String, organization: String, repository: String): Unit =
      val root = s"https://github.com/$organization/$repository"
      val raw = s"$root/raw/$branch"
      val blob = s"$root/blob/$branch"

      element
        .querySelectorAll("img,a")
        .filter(e => !Seq("href", "src").flatMap(a => Option(e.getAttribute(a))).head.startsWith("http"))
        .foreach { e =>
          val isAnchor = e.tagName == "A"
          val attr = if isAnchor then "href" else "src"
          if isAnchor then e.setAttribute("target", "_blank")

          Option(e.getAttribute(attr))
            .filter(_.nonEmpty)
            .foreach { oldUrl =>
              val (_, newUrl) = resolveReadmeLinkUrl(isAnchor, oldUrl, root, raw, blob)
              e.setAttribute(attr, newUrl)
            }
        }
    end fixImages

    val repoRequest: Request = new Request(
      s"https://api.github.com/repos/$organization/$repository",
      new RequestInit:
        this.headers = headersWithCreds.toJSDictionary
    )
    fetch(repoRequest).toFuture
      .flatMap { res =>
        if res.status == 200 then res.text().toFuture.map(extractDefaultBranch)
        else Future.successful("master")
      }
      .foreach(branch => fixImages(branch, organization, repository))
  end fixReadmeLinks

  @JSExport
  def main(token: UndefOr[String]): Unit =
    document.addEventListener[KeyboardEvent]("keydown", jumpToSearchInput(_))

    val autocompletion = new Autocompletion()

    Dom.getSearchInput.foreach { input =>
      input.addEventListener[Event]("input", autocompletion.run(_))
      input.addEventListener[KeyboardEvent]("keydown", autocompletion.navigate(_))
    }

    Dom.getById[Element]("README").foreach(fixReadmeLinks(_, token.toOption))

    val config =
      js.Dictionary[js.Any]("img_dir" -> "https://cdnjs.cloudflare.com/ajax/libs/emojify.js/1.1.0/images/basic")
    emojify.setConfig(config)
    Dom.getAllByClassNames[Element]("emojify").foreach(emojify.run)
    emojify.run(document.body)

    CopyToClipboard.addCopyListenersOnClass("btn-copy")

    ActiveNavObserver.start()
  end main

  @JSExport
  def createSparkline(): Unit = Sparkline.createCommitActivity()

  @JSExport
  def createInsightsChart(): Unit = Insights.createChart()

  @JSExport
  def updateVisibleArtifactsInGrid(): Unit =
    def valuesOfCheckedInputsWithName(name: String): Set[String] =
      Dom
        .getAllBySelectors[HTMLInputElement](s"input[name='$name']")
        .filter(_.checked)
        .map(_.value)
        .toSet

    val selectedPlatforms = valuesOfCheckedInputsWithName("platform")
    val selectedBinaryVersions = valuesOfCheckedInputsWithName("binary-version")
    val allRequiredClasses = selectedPlatforms ++ selectedBinaryVersions

    Dom.getAllByClassNames[Element]("artifact-line").foreach { e =>
      val supported = allRequiredClasses.forall(e.classList.contains(_))
      if supported then
        e.classList.remove("artifact-line-hidden")
        e.classList.add("artifact-line-visible")
      else
        e.classList.remove("artifact-line-visible")
        e.classList.add("artifact-line-hidden")
    }

    Dom.getAllByClassNames[Element]("version-line").foreach { e =>
      val supportedCount = e.querySelectorAll("tr.artifact-line-visible").length

      val versionCell = e.querySelector("td.version").asInstanceOf[HTMLTableCellElement]
      versionCell.rowSpan = supportedCount + 1

      if supportedCount != 0 then
        e.classList.remove("version-line-hidden")
        e.classList.add("version-line-visible")
      else
        e.classList.remove("version-line-visible")
        e.classList.add("version-line-hidden")
    }
  end updateVisibleArtifactsInGrid
end Client
