package ch.epfl.scala.index.server

import scala.util.Success
import scala.util.Try

import ch.epfl.scala.index.server.config.ServerConfig
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ServerConfigTests extends AnyFunSpec with Matchers {
  describe("AppConf") {
    it("should load the conf") {
      Try(ServerConfig.load()) shouldBe a[Success[_]]
    }
  }
}
