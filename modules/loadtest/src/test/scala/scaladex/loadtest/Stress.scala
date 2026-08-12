package scaladex.loadtest

import scala.concurrent.duration.*

import io.gatling.core.Predef.*
import io.gatling.core.controller.inject.open.ConstantRateOpenInjection
import io.gatling.core.controller.inject.open.OpenInjectionStep
import io.gatling.core.controller.inject.open.RampRateOpenInjection

object Stress:
  val maxRate: Double = sys.props.getOrElse("loadtest.maxRate", "100").toDouble
  val rampDuration: Int = sys.props.getOrElse("loadtest.rampDuration", "100").toInt
  val constantDuration: Int = sys.props.getOrElse("loadtest.constantDuration", "100").toInt

  def ramp: RampRateOpenInjection = rampUsersPerSec(1).to(maxRate).during(rampDuration.seconds)

  def constant: ConstantRateOpenInjection = constantUsersPerSec(maxRate).during(constantDuration.seconds)

  def rampThenConstant: Seq[OpenInjectionStep] = Seq(ramp, constant)
end Stress
