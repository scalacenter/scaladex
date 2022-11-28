package scaladex.client
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.scalajs.dom._

/**
 * Create the ChartJS line chart with the commit activity
  */
object Sparkline {

  def createCommitActivity(): Unit =
    Dom.getById[HTMLCanvasElement]("commit-activity").foreach(createSparkline)

  def createSparkline(canvas: HTMLCanvasElement): Unit = {
    val commits = canvas.getAttribute("data-commit-activity-count").split(",").map(_.toDouble).toSeq
    val startingDay = canvas.getAttribute("data-commit-activity-starting-day")
    if (startingDay.nonEmpty) {
      val startDate = Instant.ofEpochSecond(startingDay.toLong)
      val data = commits.zipWithIndex.map {
        case (commit, index) => DataPoint(startDate.plus(index * 7, ChronoUnit.DAYS).toEpochMilli.toDouble, commit)
      }
      val ctx = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
      val grad = ctx.createLinearGradient(0, 0, canvas.width, canvas.height)
      grad.addColorStop(0, "rgb(0, 122, 201)"); // Initial path colour
      grad.addColorStop(1, "rgb(0, 201, 114)"); // End stroke colour
      val fillGrad = ctx.createLinearGradient(0, 0, canvas.width, canvas.height)
      fillGrad.addColorStop(0, "rgba(0, 122, 201, 0.5)"); // Initial path colour
      fillGrad.addColorStop(1, "rgba(0, 201, 114, 0.5)"); // End stroke colour

      val chartOptions = ChartOptions(
        plugins = PluginOptions(
          tooltip = TooltipOptions(enabled = false),
          legend = LegendOptions(display = false, FontOptions(size = 10))
        ),
        elements = ElementOptions(
          line = LineOptions(
            backgroundColor = fillGrad,
            borderColor = grad,
            borderWidth = 1,
            fill = "origin"
          ),
          point = new PointOptions {
            radius = 0
            hitRadius = 10
          }
        ),
        scales = ScaleOptions(
          x = AxisTimeOptions(
            time = TimeOptions(
              unit = "month"
            )
          ),
          y = AxisOptions(
            ticks = TicksOptions(stepSize = 1),
            min = 0
          )
        )
      )
      new Chart(
        ctx,
        Chart.Line(
          ChartData(Seq(ChartDataset(data, "Commit count"))),
          chartOptions
        )
      )
    }
  }
}
