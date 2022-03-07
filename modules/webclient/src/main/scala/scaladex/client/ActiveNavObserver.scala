package scaladex.client

import org.scalajs.dom.document
import org.scalajs.dom.html.Element
import org.scalajs.dom.html.LI
import org.scalajs.dom.html.Link
import scaladex.dom.IntersectionObserver

/**
  * Find all visible sections and add the "active" class in their corresponding
  * list items in nav.
  */
object ActiveNavObserver {
  private val observer: IntersectionObserver = IntersectionObserver { entry =>
    val id = entry.target.getAttribute("id")
    val navLink = document.querySelector(s"""nav li a[href="#$id"]""").asInstanceOf[Link]
    if (navLink != null) {
      val navLI = navLink.parentElement.asInstanceOf[LI]

      if (entry.intersectionRatio > 0) navLI.classList.add("active")
      else navLI.classList.remove("active")
    }
  }

  def start(): Unit = {
    val sections = document.querySelectorAll("section[id]")
    for (i <- 0 until sections.length)
      observer.observe(sections.item(i).asInstanceOf[Element])
  }
}
