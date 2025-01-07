package scaladex.server

import scala.util.Success
import scala.util.Try

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.server.config.ServerConfig

class ServerConfigTests extends AnyFunSpec with Matchers:
  describe("AppConf") {
    it("should load the conf") {
      Try(ServerConfig.load()) shouldBe a[Success[_]]
    }
  }
