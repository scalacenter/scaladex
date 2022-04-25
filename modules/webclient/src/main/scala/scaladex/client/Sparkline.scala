package scaladex.client
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.ext._

/**
  * Find all commit activity canvas tag and draw the commit activity sparkline
  */
object Sparkline {

  def createCommitActivitySparkline(): Unit =
    document
      .querySelectorAll("canvas.commit-activity")
      .foreach { element =>
        val canvas = element.asInstanceOf[dom.raw.HTMLCanvasElement]
        createSparkline(canvas)
      }

  private def createSparkline(obj: dom.raw.HTMLCanvasElement) = {
    obj.width = obj.parentElement.clientWidth
    obj.height = obj.parentElement.clientHeight
    val ctx = obj.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    val spark = obj.getAttribute("data-commit-activity").split(",").map(_.toInt)
    val margin = 10
    val ratioW = ((obj.width - margin * 2) * 1) / spark.length
    val ratioH = ((obj.height - margin * 2) * .8) / spark.max

    val grad = ctx.createLinearGradient(0, 0, obj.width, obj.height)
    grad.addColorStop(0, "rgb(0, 122, 201)"); // Initial path colour
    grad.addColorStop(1, "rgb(0, 201, 114)"); // End stroke colour

    // Style for the fill under the sparkline
    val fillGradiant = ctx.createLinearGradient(0, 0, obj.width, obj.height)
    fillGradiant.addColorStop(0, "rgba(0, 122, 201, 0.5)"); // Initial path colour
    fillGradiant.addColorStop(1, "rgba(0, 201, 114, 0.5)"); // End stroke colour

    ctx.strokeStyle = grad
    ctx.fillStyle = fillGradiant
    ctx.lineWidth = 2
    ctx.beginPath()

    // Move to starting point
    ctx.moveTo(margin, obj.height - (spark.head * ratioH + margin))

    // Start drawing the sparkline
    spark.tail.zip(LazyList.from(1)).foreach {
      case (sparkNode, index) =>
        val x = index * ratioW + margin
        val y = obj.height - (sparkNode * ratioH + margin)
        ctx.lineTo(x, y)
    }
    ctx.stroke()

    // Wrapping up back to the beggining in order to fill the bottom part of the chart
    ctx.lineTo((spark.length - 1) * ratioW + margin, obj.height)
    ctx.lineTo(margin, obj.height)
    ctx.fill()
  }
}
