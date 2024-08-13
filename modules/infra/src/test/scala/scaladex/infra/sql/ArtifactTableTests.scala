package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Jvm
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.infra.BaseDatabaseSuite

class ArtifactTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  import ArtifactTable._
  it("check insertIfNotExist")(check(insertIfNotExist))
  it("check selectAllArtifacts") {
    check(selectAllArtifacts(None, None))
    check(selectAllArtifacts(Some(Scala.`2.13`), None))
    check(selectAllArtifacts(None, Some(Jvm)))
    check(selectAllArtifacts(Some(Scala.`3`), Some(ScalaJs.`1.x`)))
  }
  it("check selectArtifactByGroupIdAndArtifactId")(check(selectArtifactByGroupIdAndArtifactId))
  it("check selectArtifactByProject")(check(selectArtifactByProject))
  it("check selectArtifactByProjectAndName")(check(selectArtifactByProjectAndName))
  it("check selectOldestByProject")(check(selectOldestByProject))
  it("check updateProjectRef")(check(updateProjectRef))
  it("check selectGroupIds")(check(selectGroupIds))
  it("check selectMavenReference")(check(selectMavenReference))
  it("check updateReleaseDate")(check(updateReleaseDate))
  it("check selectByMavenReference")(check(selectByMavenReference))
  it("check countVersionsByProject")(check(countVersionsByProject))
  it("check selectArtifactByParams") {
    check(selectArtifactByParams(false))
    check(selectArtifactByParams(true))
  }
  it("check selectMavenReferenceWithNoReleaseDate")(check(selectMavenReferenceWithNoReleaseDate))
  it("check selectLatestArtifacts")(check(selectLatestArtifacts))
  it("check setLatestVersion")(check(setLatestVersion))
  it("check unsetOthersLatestVersion")(check(unsetOthersLatestVersion))
}
