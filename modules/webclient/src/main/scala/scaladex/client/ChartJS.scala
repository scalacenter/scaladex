package scaladex.client

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import org.scalajs.dom.raw.CanvasGradient
import org.scalajs.dom

@js.native
trait ChartDataset extends js.Object {
  def label: String = js.native
  def color: CanvasGradient = js.native
  def data: js.Array[Double] = js.native
  def datasets: js.Array[ChartDataset] = js.native
  def pointRadius: Int = js.native
}

object ChartDataset {
  def apply(
      data: Seq[Double],
      label: String
  ): ChartDataset =
    js.Dynamic
      .literal(
        data = data.toJSArray,
        label = label
      )
      .asInstanceOf[ChartDataset]
}

@js.native
trait ChartData extends js.Object {
  def labels: js.Array[String] = js.native
  def datasets: js.Array[ChartDataset] = js.native
}

object ChartData {
  def apply(labels: Seq[String], datasets: Seq[ChartDataset]): ChartData =
    js.Dynamic
      .literal(
        labels = labels.toJSArray,
        datasets = datasets.toJSArray
      )
      .asInstanceOf[ChartData]
}

@js.native
trait ChartConfig extends js.Object {
  def `type`: String = js.native
  def data: ChartData = js.native
  def options: ChartOptions = js.native
}

object ChartConfig {
  def apply(`type`: String, data: ChartData, options: ChartOptions): ChartConfig =
    js.Dynamic
      .literal(
        `type` = `type`,
        data = data,
        options = options
      )
      .asInstanceOf[ChartConfig]
}

@js.native
trait ChartOptions extends js.Object {
  def plugins: PluginOptions = js.native
  def elements: ElementOptions = js.native
}

object ChartOptions {
  def apply(plugins: PluginOptions, elements: ElementOptions): ChartOptions =
    js.Dynamic
      .literal(
        plugins = plugins,
        elements = elements
      )
      .asInstanceOf[ChartOptions]
}

@js.native
trait PluginOptions extends js.Object {
  def legend: LegendOptions = js.native
}
object PluginOptions {
  def apply(legend: LegendOptions): PluginOptions =
    js.Dynamic
      .literal(
        legend = legend
      )
      .asInstanceOf[PluginOptions]
}

@js.native
trait ElementOptions extends js.Object {
  def line: LineOptions = js.native
  def point: PointOptions = js.native
}
object ElementOptions {
  def apply(line: LineOptions, point: PointOptions): ElementOptions =
    js.Dynamic
      .literal(
        line = line,
        point = point
      )
      .asInstanceOf[ElementOptions]
}

@js.native
trait LineOptions extends js.Object {
  def borderColor: CanvasGradient = js.native
}
object LineOptions {
  def apply(borderColor: CanvasGradient): LineOptions =
    js.Dynamic
      .literal(
        borderColor = borderColor
      )
      .asInstanceOf[LineOptions]
}

@js.native
trait PointOptions extends js.Object {
  def radius: Int = js.native
}
object PointOptions {
  def apply(radius: Int): PointOptions =
    js.Dynamic
      .literal(
        radius = radius
      )
      .asInstanceOf[PointOptions]
}
@js.native
trait LegendOptions extends js.Object {
  def display: Boolean = js.native
  def font: FontOptions = js.native
}

object LegendOptions {
  def apply(display: Boolean, font: FontOptions): LegendOptions =
    js.Dynamic.literal(display = display, font = font).asInstanceOf[LegendOptions]
}

@js.native
trait FontOptions extends js.Object {
  def family: String = js.native
  def size: Int = js.native
  def style: String = js.native
  def weight: String = js.native
}

object FontOptions {
  def apply(
      family: String = "'Helvetica Neue', 'Helvetica', 'Arial', sans-serif",
      size: Int = 12,
      style: String = "normal",
      weight: String = "normal"
  ): FontOptions =
    js.Dynamic.literal(family = family, size = size, style = style, weight = weight).asInstanceOf[FontOptions]
}
// define a class to access the Chart.js component
@js.native
@js.annotation.JSGlobal
class Chart(ctx: dom.CanvasRenderingContext2D, config: ChartConfig) extends js.Object

object Chart {
  // create different kinds of charts
  def Line(data: ChartData, options: ChartOptions): ChartConfig = ChartConfig("line", data, options)
  def Bar(data: ChartData, options: ChartOptions): ChartConfig = ChartConfig("bar", data, options)
}
