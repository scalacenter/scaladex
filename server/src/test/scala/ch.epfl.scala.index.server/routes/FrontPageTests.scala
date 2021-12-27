package ch.epfl.scala.index.server.routes

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Platform
import scaladex.core.model.Project

class FrontPageTests extends AnyFunSpec with Matchers {
  import scaladex.core.model.ScalaVersion._
  import scaladex.core.model.Scala3Version._
  import Platform.PlatformType._

  describe("FrontPageTests") {
    val ref1: Project.Reference = Project.Reference.from("a", "b")
    val ref2: Project.Reference = Project.Reference.from("c", "d")

    val platformWithCount: Map[Project.Reference, Set[Platform]] = Map(
      ref1 -> Set(
        Platform.Java,
        Platform.ScalaNative(`2.11`, Platform.ScalaNative.`0.3`),
        Platform.ScalaNative(`2.12`, Platform.ScalaNative.`0.3`),
        Platform.ScalaNative(`2.12`, Platform.ScalaNative.`0.4`)
      ),
      ref2 -> Set(
        Platform.ScalaNative(`2.13`, Platform.ScalaNative.`0.3`),
        Platform.ScalaJvm(`2.13`),
        Platform.ScalaJvm(`2.12`),
        Platform.ScalaJvm(`3.0.0-RC3`),
        Platform.Java
      )
    )
    it("getPlatformTypeWithCount") {
      val res = FrontPage.getPlatformTypeWithCount(platformWithCount)
      res shouldBe List((Java, 2), (Native, 2), (Jvm, 1))
    }
    it("getScalaLanguageVersionWithCount") {
      val res = FrontPage.getScalaLanguageVersionWithCount(platformWithCount)
      res shouldBe List(
        (`2.11`.family, 1),
        (`2.12`.family, 2),
        (`2.13`.family, 1),
        (`3`.family, 1)
      )
    }
    it("should collect ScalaNative") {
      val res = FrontPage.getPlatformWithCount(platformWithCount) { case p: Platform.ScalaNative => p.scalaNativeV }
      res shouldBe List(
        (BinaryVersion.parse("0.3").get, 2),
        (BinaryVersion.parse("0.4").get, 1)
      )
    }
  }

}
