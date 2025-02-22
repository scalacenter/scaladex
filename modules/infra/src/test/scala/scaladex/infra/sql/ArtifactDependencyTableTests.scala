package scaladex.infra.sql

import scaladex.infra.BaseDatabaseSuite

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ArtifactDependencyTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers:
  it("check insertIfNotExist")(check(ArtifactDependencyTable.insertIfNotExist))
  it("check select")(check(ArtifactDependencyTable.select))
  it("check selectDirectDependency")(check(ArtifactDependencyTable.selectDirectDependency))
  it("check selectReverseDependency")(check(ArtifactDependencyTable.selectReverseDependency))
  it("check computeProjectDependencies")(check(ArtifactDependencyTable.computeProjectDependencies))
