package ch.epfl.scala.index.server.routes

import ch.epfl.scala.index.model.release.BinaryVersion
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.newModel.NewProject
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class FrontPageTests extends AnyFunSpec with Matchers {
  import ch.epfl.scala.index.server.Values._
  import ScalaVersion._
  import Scala3Version._
  import Platform.PlatformType._

  describe("FrontPageTests") {
    val ref1: NewProject.Reference = NewProject.Reference.from("a", "b")
    val ref2: NewProject.Reference = NewProject.Reference.from("c", "d")

    val platformWithCount: Map[NewProject.Reference, Set[Platform]] = Map(
      ref1 -> Set(
        Platform.Java,
        Platform.ScalaNative(`2.11`, nat03),
        Platform.ScalaNative(`2.12`, nat03),
        Platform.ScalaNative(`2.12`, nat04)
      ),
      ref2 -> Set(
        Platform.ScalaNative(`2.13`, nat03),
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
    it("should collect scalaJs") {
      val res = FrontPage.getPlatformWithCount(platformWithCount) {
        case p: Platform.ScalaNative => p.scalaNativeV
      }
      res shouldBe List(
        (BinaryVersion.parse("0.3.0").get, 2),
        (BinaryVersion.parse("0.4.0").get, 1)
      )
    }
  }

}
