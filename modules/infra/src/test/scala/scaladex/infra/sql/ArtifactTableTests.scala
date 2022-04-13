package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.BaseDatabaseSuite

class ArtifactTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertIfNotExist")(check(ArtifactTable.insertIfNotExist))
  it("check selectAllArtifacts")(check(ArtifactTable.selectAllArtifacts))
  it("check selectArtifactByLanguage")(check(ArtifactTable.selectArtifactByLanguage))
  it("check selectArtifactByPlatform")(check(ArtifactTable.selectArtifactByPlatform))
  it("check selectArtifactByLanguageAndPlatform")(check(ArtifactTable.selectArtifactByLanguageAndPlatform))
  it("check selectArtifactByProject")(check(ArtifactTable.selectArtifactByProject))
  it("check selectArtifactByProjectAndName")(check(ArtifactTable.selectArtifactByProjectAndName))
  it("check findOldestArtifactsPerProjectReference")(check(ArtifactTable.selectOldestByProject))
  it("check updateProjectRef")(check(ArtifactTable.updateProjectRef))
  it("check selectGroupIdWithArtifactId")(check(ArtifactTable.selectGroupIds))
  it("check selectMavenReference")(check(ArtifactTable.selectMavenReference))
  it("check updateReleaseDate")(check(ArtifactTable.updateReleaseDate))
  it("check selectByMavenReference")(check(ArtifactTable.selectByMavenReference))
  it("check getReleasesFromArtifacts")(check(ArtifactTable.getReleasesFromArtifacts))
}
