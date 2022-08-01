package scaladex.client
import org.scalajs.dom
import org.scalajs.dom.document

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.LocalDate

/**
  * Find all commit activity canvas tag and draw the commit activity sparkline
  */
object Sparkline {

  def createCommitActivitySparkline(): Unit = {
    val canvas = document.querySelector("#commit-activity").asInstanceOf[dom.raw.HTMLCanvasElement]
    val commits = canvas.getAttribute("data-commit-activity-count").split(",").map(_.toDouble).toSeq
    val startingDay = canvas.getAttribute("data-commit-activity-starting-day")
    val endDay = canvas.getAttribute("data-commit-activity-last-day")
    if (startingDay.nonEmpty && endDay.nonEmpty) {
      val firstDay = LocalDateTime.ofEpochSecond(startingDay.toLong, 0, ZoneOffset.UTC).toLocalDate()
      val lastDay = LocalDateTime.ofEpochSecond(endDay.toLong, 0, ZoneOffset.UTC).toLocalDate()
      val days = firstDay.toEpochDay.to(lastDay.toEpochDay).map(LocalDate.ofEpochDay(_).getMonth())
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
            radius = 0
          )
        )
      )
      val t =
        new Chart(
          ctx,
          Chart.Line(
            ChartData(days.map(_.toString), Seq(ChartDataset(commits, "Commit count"))),
            chartOptions
          )
        )
    }
  }

}
