package scaladex.infra.sql

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.DatabaseSuite
import scaladex.infra.sql.ArtifactDependencyTable

class ArtifactDependencyTableTests extends AnyFunSpec with DatabaseSuite with Matchers {
  it("check insertIfNotExist")(check(ArtifactDependencyTable.insertIfNotExist))
  it("check select")(check(ArtifactDependencyTable.select))
  it("check selectDirectDependency")(check(ArtifactDependencyTable.selectDirectDependency))
  it("check selectReverseDependency")(check(ArtifactDependencyTable.selectReverseDependency))
  it("check computeProjectDependencies")(check(ArtifactDependencyTable.computeProjectDependencies))
  it("check computeReleaseDependency")(check(ArtifactDependencyTable.computeReleaseDependency))
}
