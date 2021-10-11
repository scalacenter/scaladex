package `ch.epfl.scala.index.server`

import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.BinaryVersion
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.server.BadgeTools
import ch.epfl.scala.index.server.Values
import ch.epfl.scala.index.server.routes.ControllerBaseSuite
import org.scalatest.BeforeAndAfterAll

class BadgeToolsTests()
    extends ControllerBaseSuite
    with BeforeAndAfterAll
    with ScalatestRouteTest {

  override def insertMockData(): Unit = {
    await(db.insertReleases(Values.catsCores)).get
  }
  override def beforeAll(): Unit = insertMockData()

  describe("when platform version is provided") {
    it(
      "mostRecentByScalaVersion should retrieve the most recent version for every scala version on Jvm"
    ) {
      val releases = await(
        db.findReleases(
          Project.Reference("typelevel", "cats"),
          NewRelease.ArtifactName("cats-core")
        )
      ).get
      val result =
        BadgeTools.mostRecentByScalaVersion(Platform.PlatformType.Jvm, None)(
          releases
        )

      result should contain theSameElementsAs List(
        ("2.13", SemanticVersion(2, Some(6), Some(1))),
        ("2.12", SemanticVersion(2, Some(6), Some(1))),
        ("3", SemanticVersion(2, Some(6), Some(1)))
      )
    }

    it(
      "mostRecentByScalaVersion should retrieve the most recent version for every scala version on Js 1.2.5"
    ) {
      val releases = await(
        db.findReleases(
          Project.Reference("typelevel", "cats"),
          NewRelease.ArtifactName("cats-core")
        )
      ).get
      val result = BadgeTools.mostRecentByScalaVersion(
        Platform.PlatformType.Js,
        BinaryVersion.parse("1.2.5")
      )(releases)

      result should contain theSameElementsAs List(
        ("2.13", SemanticVersion(2, Some(5), Some(1))),
        ("2.12", SemanticVersion(2, Some(5), Some(1))),
        ("3", SemanticVersion(2, Some(5), Some(1)))
      )
    }
  }

  describe("when no platform version is provided") {
    it(
      "mostRecentByScalaVersionAndPlatVersion should retrieve the most recent version for every scala version on Jvm"
    ) {
      val releases = await(
        db.findReleases(
          Project.Reference("typelevel", "cats"),
          NewRelease.ArtifactName("cats-core")
        )
      ).get
      val (pt, pv, ls) = BadgeTools.mostRecentByScalaVersionAndPlatVersion(
        Platform.PlatformType.Jvm
      )(releases)

      pt shouldEqual Platform.PlatformType.Jvm
      pv shouldEqual None

      ls should contain theSameElementsAs List(
        ("2.13", SemanticVersion(2, Some(6), Some(1))),
        ("2.12", SemanticVersion(2, Some(6), Some(1))),
        ("3", SemanticVersion(2, Some(6), Some(1)))
      )
    }

    it(
      "mostRecentByScalaVersion should retrieve the most recent version for every scala version on the most recent Js version"
    ) {
      val releases = await(
        db.findReleases(
          Project.Reference("typelevel", "cats"),
          NewRelease.ArtifactName("cats-core")
        )
      ).get
      val (pt, pv, ls) = BadgeTools.mostRecentByScalaVersionAndPlatVersion(
        Platform.PlatformType.Js
      )(releases)

      pt shouldEqual Platform.PlatformType.Js
      pv shouldEqual BinaryVersion.parse("1.7.1")

      ls should contain theSameElementsAs List(
        ("2.13", SemanticVersion(2, Some(5), Some(1))),
        ("2.12", SemanticVersion(2, Some(5), Some(1))),
        ("3", SemanticVersion(2, Some(5), Some(1)))
      )
    }
  }
}
