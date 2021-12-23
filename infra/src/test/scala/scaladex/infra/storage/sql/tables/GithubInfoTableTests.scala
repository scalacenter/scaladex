package scaladex.infra.storage.sql.tables

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.infra.storage.sql.BaseDatabaseSuite

class GithubInfoTableTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {
  import scaladex.infra.Values._

  import scaladex.infra.storage.sql.tables.GithubInfoTable._
  describe("should generate the query for") {
    it("insert") {
      val q = insert(Scalafix.githubInfo)
      check(q)
      q.sql shouldBe
        s"""INSERT INTO github_info (organization, repository,
           | homepage, description, logo, stars, forks, watchers, issues, creation_date, readme,
           | contributors, commits, topics, contributing_guide,
           | code_of_conduct, chatroom, open_issues) VALUES (?, ?, ?, ?,
           | ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
    }
    it("insertOrUpdate") {
      val q = insertOrUpdate(Scalafix.githubInfo)
      check(q)
      q.sql shouldBe
        s"""INSERT INTO github_info (organization, repository,
           | homepage, description, logo, stars, forks, watchers, issues, creation_date, readme,
           | contributors, commits, topics, contributing_guide,
           | code_of_conduct, chatroom, open_issues) VALUES (?, ?, ?, ?,
           | ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           | ON CONFLICT (organization, repository) DO UPDATE SET homepage=?, description=?,
           | logo=?, stars=?, forks=?, watchers=?, issues=?, creation_date=?, readme=?, contributors=?,
           | commits=?, topics=?, contributing_guide=?, code_of_conduct=?, chatroom=?, open_issues=?""".stripMargin
          .filterNot(_ == '\n')
    }
    it("selectAllTopics") {
      val q = selectAllTopics()
      check(q)
      q.sql shouldBe s"""SELECT topics FROM github_info where topics != ''"""
    }
  }

}
