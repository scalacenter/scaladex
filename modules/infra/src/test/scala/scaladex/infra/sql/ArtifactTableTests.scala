package scaladex.infra.sql

import scaladex.core.model.*
import scaladex.infra.BaseDatabaseSuite

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ArtifactTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers:
  import ArtifactTable.*
  it("check insertIfNotExist")(check(insertIfNotExist))
  it("check selectArtifactRefByProject") {
    check(selectArtifactRefByProject(true))
    check(selectArtifactRefByProject(true))
  }
  it("check selectArtifactRefByProjectAndName")(check(selectArtifactRefByProjectAndName))
  it("check selectArtifactRefByProjectAndVersion")(check(selectArtifactRefByProjectAndVersion))
  it("check selectAllArtifacts") {
    check(selectAllArtifacts(None, None))
    check(selectAllArtifacts(Some(Scala.`2.13`), None))
    check(selectAllArtifacts(None, Some(Jvm)))
    check(selectAllArtifacts(Some(Scala.`3`), Some(ScalaJs.`1.x`)))
  }
  it("check selectArtifactByGroupIdAndArtifactId")(check(selectArtifactByGroupIdAndArtifactId))
  it("check selectArtifactByProject")(check(selectArtifactByProject))
  it("check selectArtifactByProjectAndName") {
    check(selectArtifactByProjectAndName(stableOnly = true))
    check(selectArtifactByProjectAndName(stableOnly = false))
  }
  it("check selectOldestByProject")(check(selectOldestByProject))
  it("check updateProjectRef")(check(updateProjectRef))
  it("check selectGroupIds")(check(selectGroupIds))
  it("check selectReferences")(check(selectReferences))
  it("check selectReferencesByProject")(check(selectReferencesByProject))
  it("check updateReleaseDate")(check(updateReleaseDate))
  it("check selectByReference")(check(selectByReference))
  it("check countVersionsByProject")(check(countVersionsByProject))
  it("check selectMavenReferenceWithNoReleaseDate")(check(selectMavenReferenceWithNoReleaseDate))
  it("check selectVersionByGroupIdAndArtifactId") {
    check(selectVersionByGroupIdAndArtifactId(false))
    check(selectVersionByGroupIdAndArtifactId(true))
  }
  it("check selectLatestArtifact")(check(selectLatestArtifact))
  it("check selectLatestArtifacts")(check(selectLatestArtifacts))
  it("check setLatestVersion")(check(setLatestVersion))
  it("check unsetOthersLatestVersion")(check(unsetOthersLatestVersion))
end ArtifactTableTests
