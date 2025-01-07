package scaladex.client

import scala.annotation.nowarn
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

import org.scalajs.dom
import org.scalajs.dom.CanvasGradient

@js.native
trait ChartDataset extends js.Object {
  def label: String = js.native
  def data: js.Array[DataPoint] = js.native
}

object ChartDataset {
  def apply(data: Seq[DataPoint], label: String): ChartDataset =
    js.Dynamic
      .literal(data = data.toJSArray, label = label)
      .asInstanceOf[ChartDataset]
}
@js.native
trait DataPoint extends js.Object {
  def x: Double = js.native
  def y: Double = js.native
}

object DataPoint {
  def apply(x: Double, y: Double): DataPoint =
    js.Dynamic
      .literal(x = x, y = y)
      .asInstanceOf[DataPoint]
}

@js.native
trait ChartData extends js.Object {
  def datasets: js.Array[ChartDataset] = js.native
}

object ChartData {
  def apply(datasets: Seq[ChartDataset]): ChartData =
    js.Dynamic
      .literal(
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
  def scales: ScaleOptions = js.native
}

object ChartOptions {
  def apply(plugins: PluginOptions, elements: ElementOptions, scales: ScaleOptions): ChartOptions =
    js.Dynamic
      .literal(
        plugins = plugins,
        elements = elements,
        scales = scales
      )
      .asInstanceOf[ChartOptions]
}

@js.native
trait PluginOptions extends js.Object {
  def tooltip: TooltipOptions = js.native
  def legend: LegendOptions = js.native
}
object PluginOptions {
  def apply(tooltip: TooltipOptions, legend: LegendOptions): PluginOptions =
    js.Dynamic.literal(legend = legend).asInstanceOf[PluginOptions]
}

@js.native
trait ElementOptions extends js.Object {
  def line: LineOptions = js.native
  def point: PointOptions = js.native
}

object ElementOptions {
  def apply(line: LineOptions, point: PointOptions): ElementOptions =
    js.Dynamic.literal(line = line, point = point).asInstanceOf[ElementOptions]
}

@js.native
trait LineOptions extends js.Object {
  def borderColor: CanvasGradient = js.native
}
object LineOptions {
  def apply(backgroundColor: CanvasGradient, borderColor: CanvasGradient, borderWidth: Int, fill: String): LineOptions =
    js.Dynamic
      .literal(backgroundColor = backgroundColor, borderColor = borderColor, borderWidth = borderWidth, fill = fill)
      .asInstanceOf[LineOptions]
}

trait PointOptions extends js.Object {
  var radius: js.UndefOr[Int] = js.undefined
  var hitRadius: js.UndefOr[Int] = js.undefined
  var borderColor: js.UndefOr[String] = js.undefined
}

@js.native
trait ScaleOptions extends js.Object {
  def x: AxisOptions = js.native
  def y: AxisOptions = js.native
}
object ScaleOptions {
  def apply(x: AxisOptions, y: AxisOptions): ScaleOptions =
    js.Dynamic
      .literal(
        x = x,
        y = y
      )
      .asInstanceOf[ScaleOptions]
}

@js.native
trait AxisOptions extends js.Object {
  def `type`: String = js.native
  def ticks: TicksOptions = js.native
  def min: js.UndefOr[Double] = js.native
  def max: js.UndefOr[Double] = js.native
}
object AxisOptions {
  def apply(
      `type`: String = "linear",
      ticks: TicksOptions = TicksOptions(),
      min: js.UndefOr[Double] = js.undefined,
      max: js.UndefOr[Double] = js.undefined
  ): AxisOptions =
    js.Dynamic
      .literal(
        `type` = `type`,
        ticks = ticks,
        min = min,
        max = max
      )
      .asInstanceOf[AxisOptions]
}

@js.native
trait AxisTimeOptions extends AxisOptions {
  def time: TimeOptions = js.native
}
object AxisTimeOptions {
  def apply(time: TimeOptions): AxisTimeOptions =
    js.Dynamic
      .literal(
        `type` = "time",
        time = time
      )
      .asInstanceOf[AxisTimeOptions]
}
@js.native
trait TicksOptions extends js.Object {
  def stepSize: Double = js.native
}
object TicksOptions {
  def apply(stepSize: Double = 0.5): TicksOptions =
    js.Dynamic
      .literal(
        stepSize = stepSize
      )
      .asInstanceOf[TicksOptions]
}

@js.native
trait TimeOptions extends js.Object {
  def unit: String = js.native
}
object TimeOptions {
  def apply(unit: String): TimeOptions =
    js.Dynamic
      .literal(
        unit = unit
      )
      .asInstanceOf[TimeOptions]
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
trait TooltipOptions extends js.Object {
  def enabled: Boolean = js.native
}

object TooltipOptions {
  def apply(enabled: Boolean): TooltipOptions =
    js.Dynamic.literal(enabled = enabled).asInstanceOf[TooltipOptions]
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
@nowarn("msg=unused explicit parameter")
class Chart(ctx: dom.CanvasRenderingContext2D, config: ChartConfig) extends js.Object

object Chart {
  // create different kinds of charts
  def Line(data: ChartData, options: ChartOptions): ChartConfig = ChartConfig("line", data, options)
  def Bar(data: ChartData, options: ChartOptions): ChartConfig = ChartConfig("bar", data, options)
}
