package scaladex.infra.sql

import scaladex.infra.BaseDatabaseSuite

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class DiscoveredGroupIdTableTests extends AnyFunSpec with BaseDatabaseSuite with Matchers:
  it("check insertIfNotExists")(check(DiscoveredGroupIdTable.insertIfNotExists))
  it("check selectAll")(check(DiscoveredGroupIdTable.selectAll))
  it("check selectByStatus")(check(DiscoveredGroupIdTable.selectByStatus))
  it("check selectPendingToSync")(check(DiscoveredGroupIdTable.selectPendingToSync))
  it("check selectPendingToReview")(check(DiscoveredGroupIdTable.selectPendingToReview))
  it("check updateSync")(check(DiscoveredGroupIdTable.updateSync))
  it("check updateError")(check(DiscoveredGroupIdTable.updateError))
  it("check updateStatus")(check(DiscoveredGroupIdTable.updateStatus))
  it("check cursor select")(check(DiscoveredIndexCursorTable.select))
  it("check cursor upsert")(check(DiscoveredIndexCursorTable.upsert))
end DiscoveredGroupIdTableTests
