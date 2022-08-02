package scaladex.client
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.scalajs.dom
import org.scalajs.dom.document

/**
 * Create the ChartJS line chart with the commit activity
  */
object Sparkline {

  def createCommitActivitySparkline(): Unit = {
    val canvas = document.querySelector("#commit-activity").asInstanceOf[dom.raw.HTMLCanvasElement]
    val commits = canvas.getAttribute("data-commit-activity-count").split(",").map(_.toDouble).toSeq
    val startingDay = canvas.getAttribute("data-commit-activity-starting-day")
    if (startingDay.nonEmpty) {
      val startDate = Instant.ofEpochSecond(startingDay.toLong)
      val data = commits.zipWithIndex.map {
        case (commit, index) => DataPoint(startDate.plus(index, ChronoUnit.DAYS).toEpochMilli().toDouble, commit)
      }
      val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
      val grad = ctx.createLinearGradient(0, 0, canvas.width, canvas.height)
      grad.addColorStop(0, "rgb(0, 122, 201)"); // Initial path colour
      grad.addColorStop(1, "rgb(0, 201, 114)"); // End stroke colour

      val chartOptions = ChartOptions(
        PluginOptions(LegendOptions(display = false, FontOptions(size = 10))),
        elements = ElementOptions(
          line = LineOptions(
            borderColor = grad
          ),
          point = PointOptions(
            radius = 2,
            borderColor = "rgb(0,201,114)"
          )
        ),
        scales = ScaleOptions(
          x = XAxisOptions(
            `type` = "time",
            time = TimeOptions(
              unit = "month"
            )
          )
        )
      )
      val t =
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
