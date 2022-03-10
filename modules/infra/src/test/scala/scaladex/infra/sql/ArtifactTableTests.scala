package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Jvm
import scaladex.core.model.Project
import scaladex.core.model.Scala
import scaladex.core.model.ScalaNative
import scaladex.core.model.web.ArtifactsPageParams
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
  it("check countVersions")(check(ArtifactTable.countVersionsByProjct))
  it("check selectArtifactByParams") {
    val default = Artifact.Name("scalafix-core")
    val projectRef = Project.Reference.from("scalacenter", "scalafix")
    val params1 = ArtifactsPageParams(Nil, None, true)
    check(ArtifactTable.selectArtifactByParams(projectRef, default, params1))
    val bv1 = BinaryVersion(ScalaNative.`0.4`, Scala.`2.12`)
    val bv2 = BinaryVersion(Jvm, Scala.`3`)
    val params2 = ArtifactsPageParams(Seq(bv1, bv2), Some(default), false)
    check(ArtifactTable.selectArtifactByParams(projectRef, default, params2))
  }
  it("check selectMavenReferenceWithNoReleaseDate")(check(ArtifactTable.selectMavenReferenceWithNoReleaseDate))
  it("check selectUniqueArtifact")(check(ArtifactTable.selectUniqueArtifacts))
  it("check findLastVersion")(check(ArtifactTable.findLastSemanticVersionNotPrerelease))
}
