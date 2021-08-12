package ch.epfl.scala.services.storage.sql.tables

import cats.effect.IO
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.newModel.NewProject
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
  val transactor: doobie.Transactor[IO] = db.xa
  private val project = NewProject("scalacenter", "scaladex", None)
  private val emptGithubInfo = GithubInfo.empty

  override def beforeAll(): Unit = db.createTables().unsafeRunSync()

  override def afterAll(): Unit = db.dropTables().unsafeRunSync()

  describe("ProjectTable") {
    import GithubInfoTable._
    describe("insert") {
      it("should generate the query") {
        val q = insert(project)(emptGithubInfo)
//        check(q)
        q.sql shouldBe s"""INSERT INTO github_info (organization, repository, name, owner,
                          | homepage, description, logo, stars, forks, watchers, issues, readme,
                          | contributors, contributorCount, commits, topics, contributingGuide,
                          | codeOfConduct, chatroom, beginnerIssuesLabel, beginnerIssues,
                          | selectedBeginnerIssues, filteredBeginnerIssues) VALUES (?, ?, ?, ?,
                          | ?, ?, ?, ?, ?, ?, ?,?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
          .filterNot(_ == '\n')
      }
    }
  }

}
