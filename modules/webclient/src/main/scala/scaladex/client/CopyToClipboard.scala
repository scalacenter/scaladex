package scaladex.client

import org.scalajs.dom
import org.scalajs.dom.Event
import org.scalajs.dom.HTMLButtonElement
import org.scalajs.dom.document

object CopyToClipboard:
  private val errorPrefix = "[CopyToClipboard] -"

  /** Add listeners on the copy buttons
    * @param triggerClass
    *   the HTML tag class identifying the buttons to listen
    */
  def addCopyListenersOnClass(triggerClass: String): Unit =
    document.getElementsByClassName(triggerClass).foreach { trigger =>
      trigger.addEventListener(
        `type` = "click",
        listener = listener(
          targetId = "data-clipboard-target",
          displayMsg = Some("Copied!")
        ),
        useCapture = true
      )
    }

  /** Listen a copy button
    * @param targetId
    *   identifies the tag to be copied
    * @param displayMsg
    *   Change the button text for 4s by the provided text
    * @return
    */
  private def listener(
      targetId: String,
      displayMsg: Option[String]
  ): Event => Unit = event =>
    event.target match
      case button: HTMLButtonElement =>
        val targetAttribute = button.getAttribute(targetId)
        val target = document.getElementById(targetAttribute)

        if target != null then
          copyToClipboard(target.textContent)

          displayMsg match
            case Some(tempMsg) =>
              val previousContent = button.textContent
              button.textContent = tempMsg
              dom.window
                .setInterval(() => button.textContent = previousContent, 4000)

            case None => ()
        else
          throw new Exception(
            s"$errorPrefix no target is associated to the given target attribute"
          )
        end if
      case _ => throw new Exception(s"$errorPrefix this is not a button")

  /** Copy to clipboard It creates a temporary _textarea_ node that holds the text to copy
    * @param text
    *   the text to copy
    */
  private def copyToClipboard(text: String): Unit =
    val textNode = document
      .createElement("textarea")
      .asInstanceOf[dom.html.TextArea]

    textNode.textContent = text
    document.body.appendChild(textNode)
    textNode.select()

    document.execCommand("copy")
    document.body.removeChild(textNode)
  end copyToClipboard
end CopyToClipboard
