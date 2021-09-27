package ch.epfl.scala.services.storage.sql.tables

import ch.epfl.scala.index.Values
import ch.epfl.scala.services.storage.sql.BaseDatabaseSuite
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class GithubInfoTableTests
    extends AsyncFunSpec
    with BaseDatabaseSuite
    with Matchers {
  import Values._

  describe("ProjectTable") {
    import GithubInfoTable._
    it("should generate the insert the query") {
      val q = insert(Scalafix.project)(Scalafix.githubInfo)
//        check(q)
      q.sql shouldBe s"""INSERT INTO github_info (organization, repository, name, owner,
                        | homepage, description, logo, stars, forks, watchers, issues, readme,
                        | contributors, contributorCount, commits, topics, contributingGuide,
                        | codeOfConduct, chatroom, beginnerIssuesLabel, beginnerIssues,
                        | selectedBeginnerIssues, filteredBeginnerIssues) VALUES (?, ?, ?, ?,
                        | ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
        .filterNot(_ == '\n')
    }
    it("should generate the insert or update query") {
      val q = insertOrUpdate(Scalafix.project)(Scalafix.githubInfo)
      q.sql shouldBe s"""INSERT INTO github_info (organization, repository, name, owner,
                        | homepage, description, logo, stars, forks, watchers, issues, readme,
                        | contributors, contributorCount, commits, topics, contributingGuide,
                        | codeOfConduct, chatroom, beginnerIssuesLabel, beginnerIssues,
                        | selectedBeginnerIssues, filteredBeginnerIssues) VALUES (?, ?, ?, ?,
                        | ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        | ON CONFLICT (organization, repository) DO UPDATE SET name=?, owner=?, homepage=?, description=?,
                        | logo=?, stars=?, forks=?, watchers=?, issues=?, readme=?, contributors=?, contributorCount=?,
                        | commits=?, topics=?, contributingGuide=?, codeOfConduct=?, chatroom=?""".stripMargin
        .filterNot(_ == '\n')
    }
  }

}
