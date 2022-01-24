package scaladex.infra.storage.sql.tables

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.storage.sql.BaseDatabaseSuite
import scaladex.infra.storage.sql.tables.ArtifactDependencyTable

class ArtifactDependencyTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers {
  it("check insertIfNotExist")(check(ArtifactDependencyTable.insertIfNotExist))
  it("check select")(check(ArtifactDependencyTable.select))
  it("check selectDirectDependency")(check(ArtifactDependencyTable.selectDirectDependency))
  it("check selectReverseDependency")(check(ArtifactDependencyTable.selectReverseDependency))
  it("check selectProjectDependency")(check(ArtifactDependencyTable.selectProjectDependency))
}
