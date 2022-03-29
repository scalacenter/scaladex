package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.BaseDatabaseSuite

class ArtifactTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertIfNotExist")(check(ArtifactTable.insertIfNotExist))
  it("check selectArtifactByProject")(check(ArtifactTable.selectArtifactByProject))
  it("check selectArtifactByProjectAndName")(check(ArtifactTable.selectArtifactByProjectAndName))
  it("check findOldestArtifactsPerProjectReference")(check(ArtifactTable.selectOldestByProject))
  it("check updateProjectRef")(check(ArtifactTable.updateProjectRef))
  it("check selectGroupIdWithArtifactId")(check(ArtifactTable.selectGroupIds))
  it("check selectMavenReference")(check(ArtifactTable.selectMavenReference))
  it("check selectMavenReferenceWithNoReleaseDate")(check(ArtifactTable.selectMavenReferenceWithNoReleaseDate))
  it("check updateReleaseDate")(check(ArtifactTable.updateReleaseDate))
}
