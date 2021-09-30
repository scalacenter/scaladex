package ch.epfl.scala.index.server.routes

import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.release.BinaryVersion
import ch.epfl.scala.index.model.release.PatchBinary
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.index.model.release.ScalaLanguageVersion
import ch.epfl.scala.index.model.release.ScalaVersion
import org.scalatest.BeforeAndAfterAll

class FrontPageTests
    extends CtrlTests
    with BeforeAndAfterAll
    with ScalatestRouteTest {

  describe("FrontPageTests") {
    import ScalaVersion._
    import Scala3Version._
    import Platform.Type._
    val js0618 = PatchBinary(0, 6, 18)
    val nat03 = PatchBinary(0, 3, 0)
    val nat04 = PatchBinary(0, 4, 0)
    val ref1 = Project.Reference("a", "b")
    val ref2 = Project.Reference("c", "d")
    val `3.0.0-RC3` =
      Platform.ScalaJvm(ScalaLanguageVersion.tryParse("3.0.0-RC3").get)
    val `3.0.0-RC1` =
      Platform.ScalaJvm(ScalaLanguageVersion.tryParse("3.0.0-RC1").get)
    val platformWithCount = Map(
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
        Platform.Java,
        `3.0.0-RC3`,
        `3.0.0-RC1`
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
        (BinaryVersion.parse("0.4.0").get, 1),
        (BinaryVersion.parse("0.3.0").get, 2)
      )
    }
  }

}
