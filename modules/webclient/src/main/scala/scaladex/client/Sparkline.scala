package scaladex.client
import org.scalajs.dom
import org.scalajs.dom.document

/**
  * Find all commit activity canvas tag and draw the commit activity sparkline
  */
object Sparkline {

  def createCommitActivitySparkline(): Unit = {
    val canvas = document.querySelector("#commit-activity").asInstanceOf[dom.raw.HTMLCanvasElement]
    createSparkline(canvas)
  }

  private def createSparkline(canvas: dom.raw.HTMLCanvasElement) = {
    canvas.width = canvas.parentElement.clientWidth
    canvas.height = canvas.parentElement.clientHeight
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    val commitsMonth = canvas.getAttribute("data-commit-activity-month").split(",").map(_.toLong)
    val commitsCount = canvas.getAttribute("data-commit-activity-count").split(",").map(_.toInt)
    val margin = canvas.getAttribute("margin").toInt

    val ratioW = Math.ceil(canvas.width / (commitsCount.length + 1.0))
    val ratioH = ((canvas.height - margin) * .9) / Math.max(commitsCount.max, 1)

    val grad = ctx.createLinearGradient(0, 0, canvas.width, canvas.height)
    grad.addColorStop(0, "rgb(0, 122, 201)"); // Initial path colour
    grad.addColorStop(1, "rgb(0, 201, 114)"); // End stroke colour

    // Style for the fill under the sparkline
    val fillGradiant = ctx.createLinearGradient(0, 0, canvas.width, canvas.height)
    fillGradiant.addColorStop(0, "rgba(0, 122, 201, 0.5)"); // Initial path colour
    fillGradiant.addColorStop(1, "rgba(0, 201, 114, 0.5)"); // End stroke colour

    ctx.strokeStyle = grad
    ctx.fillStyle = fillGradiant
    ctx.lineWidth = 2
    ctx.beginPath()

    // Move to starting point
    ctx.moveTo(0, canvas.height - (commitsCount.head * ratioH + margin))

    // Start drawing the sparkline
    commitsCount.tail.zip(LazyList.from(1)).foreach {
      case (sparkNode, index) =>
        val x = index * ratioW
        val y = canvas.height - (sparkNode * ratioH + margin)
        ctx.lineTo(x, y)
    }
    ctx.stroke()

    val lastPoint = (commitsCount.length - 1) * ratioW
    // Wrapping up back to the beggining in order to fill the bottom part of the chart
    ctx.lineTo(lastPoint, canvas.height - margin)
    ctx.lineTo(0, canvas.height - margin)
    ctx.fill()

    drawAxis(ctx, canvas, margin, lastPoint)
  }

  private def drawAxis(
      ctx: dom.CanvasRenderingContext2D,
      canvas: dom.raw.HTMLCanvasElement,
      margin: Int,
      lastPoint: Double
  ): Unit = {
    ctx.strokeStyle = "black";
    ctx.lineWidth = 1;
    ctx.beginPath();
    /* y axis along the left edge of the canvas*/
    ctx.moveTo(0, 0);
    ctx.lineTo(0, canvas.height - margin);
    ctx.stroke();
    /* x axis along the bottom edge of the canvas*/
    ctx.moveTo(0, canvas.height - margin);
    ctx.lineTo(lastPoint, canvas.height - margin);
    ctx.stroke();
  }
}
