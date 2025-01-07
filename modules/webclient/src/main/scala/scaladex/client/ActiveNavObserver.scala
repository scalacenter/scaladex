package scaladex.client

import scala.scalajs.js.timers

import org.scalajs.dom.document
import org.scalajs.dom.html.Element
import org.scalajs.dom.html.Link
import scaladex.dom.IntersectionObserver

/** Find all visible sections and add the "active" class in their corresponding list items in nav.
  */
object ActiveNavObserver:
  def start(): Unit =
    val sectionsAndNavItem =
      document
        .querySelectorAll("section[id]")
        .toSeq
        .collect { case e: Element => e }
        .flatMap { section =>
          val id = section.getAttribute("id")
          val link = document.querySelector(s"""nav li a[href="#$id"]""").asInstanceOf[Link]
          Option(link).map(l => section -> l.parentElement)
        }

    var debounceUpdate: timers.SetTimeoutHandle = null
    val observer = IntersectionObserver { _ =>
      timers.clearTimeout(debounceUpdate)
      // ignore the observed entry and update all sections instead
      debounceUpdate = timers.setTimeout(150) {
        for (section, navItem) <- sectionsAndNavItem do
          if isInViewport(section) then
            navItem.classList.add("active")
            println(s"Section ${section.getAttribute("id")} is in viewport")
          else
            navItem.classList.remove("active")
            println(s"Section ${section.getAttribute("id")} is out of viewport")
      }
    }

    for (section, _) <- sectionsAndNavItem do observer.observe(section)
  end start

  private def isInViewport(element: Element): Boolean =
    val rect = element.getBoundingClientRect()
    rect.top < document.documentElement.clientHeight && rect.bottom > 0
end ActiveNavObserver
