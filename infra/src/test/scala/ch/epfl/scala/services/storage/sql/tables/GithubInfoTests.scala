package ch.epfl.scala.services.storage.sql.tables

import cats.effect.IO
import ch.epfl.scala.services.storage.sql.Values
import doobie.scalatest.IOChecker
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class GithubInfoTests
    extends AsyncFunSpec
    with Matchers
    with IOChecker
    with BeforeAndAfterAll {
  private val db = Values.db
  val transactor: doobie.Transactor[IO] = Values.xa
  private val project = Values.project
  private val emptyGithubInfo = Values.githubInfo

  override def beforeAll(): Unit = db.migrate().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("ProjectTable") {
    import GithubInfoTable._
    it("should generate the insert the query") {
      val q = insert(project)(emptyGithubInfo)
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
      val q = insertOrUpdate(project)(emptyGithubInfo)
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
