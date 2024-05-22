package scaladex.client

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.UndefOr
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.annotation.JSExportTopLevel

import org.scalajs.dom._
import org.scalajs.dom.document
import org.scalajs.dom.ext.KeyCode

@JSExportTopLevel("ScaladexClient")
object Client {
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  private def jumpToSearchInput(event: KeyboardEvent): Unit =
    if (event.ctrlKey && event.keyCode == KeyCode.S) {
      Dom.getSearchInput.foreach { input =>
        if (event.target != input) {
          input.focus()
          event.preventDefault()
        }
      }
    }

  @js.native
  trait Repo extends js.Object {
    val default_branch: String = js.native
  }

  private def fetchAndReplaceReadme(element: Element, token: Option[String]): Unit = {

    val organization = element.attributes.getNamedItem("data-organization").value
    val repository = element.attributes.getNamedItem("data-repository").value
    val headers = Map("Accept" -> "application/vnd.github.VERSION.html")

    val headersWithCreds =
      token
        .map(t => headers + ("Authorization" -> s"bearer $t"))
        .getOrElse(headers)

    def setReadme(): Future[Unit] = {
      val readmeRequest: Request = new Request(
        s"https://api.github.com/repos/$organization/$repository/readme",
        new RequestInit {
          headers = headersWithCreds.toJSDictionary
        }
      )

      fetch(readmeRequest).toFuture
        .flatMap { res =>
          if (res.status == 200) {
            res.text().toFuture
          } else {
            Future.successful("No README found for this project, please check the repository")
          }
        }
        .map(res => element.innerHTML = res)
    }

    def getDefaultBranchAndFixImages(): Future[Unit] = {
      def extractDefaultBranch(text: String): String =
        js.JSON.parse(text).asInstanceOf[Repo].default_branch

      def fixImages(branch: String, organization: String, repository: String): Unit = {
        val root = s"https://github.com/$organization/$repository"
        val raw = s"$root/raw/$branch"
        val blob = s"$root/blob/$branch"

        element
          .querySelectorAll("img,a")
          .filter(e => !Seq("href", "src").flatMap(a => Option(e.getAttribute(a))).head.startsWith("http"))
          .foreach { e =>
            val (at, newBase) =
              if (e.tagName == "A") {
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

      val repoRequest: Request = new Request(
        s"https://api.github.com/repos/$organization/$repository",
        new RequestInit {
          headers = headersWithCreds.toJSDictionary
        }
      )
      fetch(repoRequest).toFuture
        .flatMap { res =>
          if (res.status == 200) {
            res.text().toFuture.map(extractDefaultBranch)
          } else { Future.successful("master") }
        }
        .map(branch => fixImages(branch, organization, repository))
    }

    for {
      _ <- setReadme()
      _ <- getDefaultBranchAndFixImages()
    } yield ()

  }

  @JSExport
  def main(token: UndefOr[String]): Unit = {
    document.addEventListener[KeyboardEvent]("keydown", jumpToSearchInput _)

    val autocompletion = new Autocompletion()

    Dom.getSearchInput.foreach { input =>
      input.addEventListener[Event]("input", autocompletion.run _)
      input.addEventListener[KeyboardEvent]("keydown", autocompletion.navigate _)
    }

    Dom.getById[Element]("README").foreach(fetchAndReplaceReadme(_, token.toOption))

    val config =
      js.Dictionary[js.Any]("img_dir" -> "https://cdnjs.cloudflare.com/ajax/libs/emojify.js/1.1.0/images/basic")
    emojify.setConfig(config)
    Dom.getAllByClassNames[Element]("emojify").foreach(emojify.run)
    emojify.run(document.body)

    CopyToClipboard.addCopyListenersOnClass("btn-copy")

    ActiveNavObserver.start()
  }

  @JSExport
  def createSparkline(): Unit = Sparkline.createCommitActivity()

  @JSExport
  def updateVisibleArtifactsInGrid(): Unit = {
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
      if (supported) {
        e.classList.remove("artifact-line-hidden")
        e.classList.add("artifact-line-visible")
      } else {
        e.classList.remove("artifact-line-visible")
        e.classList.add("artifact-line-hidden")
      }
    }

    Dom.getAllByClassNames[Element]("version-line").foreach { e =>
      val supportedCount = e.querySelectorAll("tr.artifact-line-visible").length

      val versionCell = e.querySelector("td.version").asInstanceOf[HTMLTableCellElement]
      versionCell.rowSpan = supportedCount + 1

      if (supportedCount != 0) {
        e.classList.remove("version-line-hidden")
        e.classList.add("version-line-visible")
      } else {
        e.classList.remove("version-line-visible")
        e.classList.add("version-line-hidden")
      }
    }
  }

}
