package ch.epfl.scala.index
package data

import me.tongfei.progressbar.ProgressBarStyle
import me.tongfei.progressbar.{ProgressBar => PB}
import org.slf4j.Logger

object ProgressBar {
  def apply(title: String, count: Int, logger: Logger): ProgressBar =
    new ProgressBar(
      new PB(title, count, 1000, System.out, ProgressBarStyle.UNICODE_BLOCK),
      logger,
      count
    )
}

class ProgressBar(inner: PB, logger: Logger, count: Int) {
  var c = 0
  var printed = 0

  def start(): Unit =
    inner.start()

  def step(): Unit = {
    inner.step()
    c += 1
    print()
  }

  def stepBy(n: Int): Unit = {
    inner.stepBy(n)
    c += n
    print()
  }

  def stop(): Unit =
    inner.stop()

  private def print(): Unit = {
    val pp = ((c.toDouble / count) * 100).toInt

    if (printed < pp) {
      logger.debug(s"$pp%")
      printed = pp
    }
  }
}
