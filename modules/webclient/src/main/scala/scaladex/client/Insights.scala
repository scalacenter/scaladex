package scaladex.client

import org.scalajs.dom.*

/** Create the ChartJS bar chart with the number of projects per Scala binary version
  */
object Insights:

  def createChart(): Unit =
    Dom.getAllBySelectors[HTMLCanvasElement]("canvas.insights-chart").foreach(renderChart)

  private def renderChart(canvas: HTMLCanvasElement): Unit =
    val labelsAttr = canvas.getAttribute("data-labels")
    val countsAttr = canvas.getAttribute("data-counts")
    val colorsAttr = canvas.getAttribute("data-colors")
    if labelsAttr != null && labelsAttr.nonEmpty && countsAttr != null && countsAttr.nonEmpty then
      val labels = labelsAttr.split(",").toIndexedSeq
      val counts = countsAttr.split(",").map(_.toDouble).toIndexedSeq
      val colors =
        if colorsAttr != null && colorsAttr.nonEmpty then colorsAttr.split(";").toIndexedSeq
        else labels.map(_ => "rgb(242, 101, 39)")
      val ctx = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
      val chartOptions = ChartOptions(
        plugins = PluginOptions(legend = LegendOptions(display = false, FontOptions(size = 10))),
        scales = ScaleOptions(
          x = AxisOptions(`type` = "category"),
          y = AxisOptions(ticks = TicksOptions(stepSize = 1), min = 0)
        ),
        maintainAspectRatio = false
      )
      new Chart(
        ctx,
        Chart.Bar(
          ChartData(labels, Seq(ChartDataset.bar(counts, "Projects", colors))),
          chartOptions
        )
      )
    end if
  end renderChart
end Insights
