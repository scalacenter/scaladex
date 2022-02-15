package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.BaseDatabaseSuite
import scaladex.infra.sql.ArtifactTable

class ArtifactTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertIfNotExist")(check(ArtifactTable.insertIfNotExist))
  it("check selectArtifactByProject")(check(ArtifactTable.selectArtifactByProject))
  it("check selectArtifactByProjectAndName")(check(ArtifactTable.selectArtifactByProjectAndName))
  it("check findOldestArtifactsPerProjectReference")(check(ArtifactTable.selectOldestByProject))
  it("check updateProjectRef")(check(ArtifactTable.updateProjectRef))
}
