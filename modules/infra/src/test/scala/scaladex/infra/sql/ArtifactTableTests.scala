package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.test.Values._
import scaladex.infra.BaseDatabaseSuite

class ArtifactTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertIfNotExist")(check(ArtifactTable.insertIfNotExist))
  it("check selectAllArtifacts")(check(ArtifactTable.selectAllArtifacts))
  it("check selectArtifactByLanguage")(check(ArtifactTable.selectArtifactByLanguage))
  it("check selectArtifactByPlatform")(check(ArtifactTable.selectArtifactByPlatform))
  it("check selectArtifactByLanguageAndPlatform")(check(ArtifactTable.selectArtifactByLanguageAndPlatform))
  it("check selectArtifactByGroupIdAndArtifactId")(check(ArtifactTable.selectArtifactByGroupIdAndArtifactId))
  it("check selectArtifactByProject")(check(ArtifactTable.selectArtifactByProject))
  it("check selectArtifactByProjectAndName")(check(ArtifactTable.selectArtifactByProjectAndName))
  it("check selectUniqueArtifact")(check(ArtifactTable.selectArtifactByProjectAndVersion))
  it("check findOldestArtifactsPerProjectReference")(check(ArtifactTable.selectOldestByProject))
  it("check updateProjectRef")(check(ArtifactTable.updateProjectRef))
  it("check selectArtifactName")(check(ArtifactTable.selectArtifactName))
  it("check selectPlatformByArtifactName")(check(ArtifactTable.selectPlatformByArtifactName))
  it("check selectGroupIds")(check(ArtifactTable.selectGroupIds))
  it("check selectMavenReference")(check(ArtifactTable.selectMavenReference))
  it("check updateReleaseDate")(check(ArtifactTable.updateReleaseDate))
  it("check selectByMavenReference")(check(ArtifactTable.selectByMavenReference))
  it("check getReleasesFromArtifacts")(check(ArtifactTable.getReleasesFromArtifacts))
  it("check countVersions")(check(ArtifactTable.countVersionsByProjct))
  it("check selectArtifactByParams") {
    check(ArtifactTable.selectArtifactByParams(Seq.empty, false))
    check(ArtifactTable.selectArtifactByParams(Seq.empty, true))
    check(ArtifactTable.selectArtifactByParams(Seq(`_sjs0.6_2.13`), false))
  }
  it("check selectMavenReferenceWithNoReleaseDate")(check(ArtifactTable.selectMavenReferenceWithNoReleaseDate))
  it("check selectLastVersion")(check(ArtifactTable.selectLastVersion))
  it("check selectLastReleaseVersion")(check(ArtifactTable.selectLastReleaseVersion))
  it("check selectLastVersionByArtifactName")(check(ArtifactTable.selectLastVersionByArtifactName))
  it("check selectLastReleaseVersionByArtifactName")(check(ArtifactTable.selectLastReleaseVersionByArtifactName))
}
