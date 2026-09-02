package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.DiscoveredGroupId
import scaladex.core.model.IndexCursor
import scaladex.core.service.MavenCentralIndexClient
import scaladex.core.service.MavenCentralIndexClient.Record
import scaladex.core.service.MavenCentralIndexClient.Result
import scaladex.core.test.InMemoryDatabase
import scaladex.core.test.Values

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class DiscoveryServiceTests extends AsyncFunSpec with Matchers:
  given ExecutionContext = ExecutionContext.global

  class StubIndexClient(remote: IndexCursor, records: Seq[Record], reached: Option[Int] = None)
      extends MavenCentralIndexClient:
    def fetchRemoteCursor(): Future[IndexCursor] = Future.successful(remote)
    def recordsSince(from: IndexCursor, to: IndexCursor, maxChunks: Int)(keep: Record => Boolean): Future[Result] =
      Future.successful(Result(records.filter(keep), reached.getOrElse(to.lastIncremental)))

  private def service(
      db: InMemoryDatabase,
      client: MavenCentralIndexClient,
      synced: collection.mutable.Buffer[String]
  ) =
    new DiscoveryService(
      db,
      client,
      groupId =>
        synced += groupId.value
        Future.successful(s"Inserted 1 poms")
    )

  it("records and syncs only unknown Scala group IDs") {
    val db = new InMemoryDatabase
    db.insertArtifacts(Seq(Values.Scalafix.artifact)) // ch.epfl.scala already indexed

    val client = new StubIndexClient(
      IndexCursor("chain-1", 100),
      Seq(
        Record("ch.epfl.scala", "scalafix-core_2.13", "0.9.31", deleted = false), // known group
        Record("dev.new", "lib_3", "1.0.0", deleted = false), // new, Scala
        Record("dev.new", "lib_sjs1_3", "1.0.0", deleted = false), // same new group
        Record("com.java", "tool", "1.0.0", deleted = false), // not Scala
        Record("dev.deleted", "gone_3", "1.0.0", deleted = true) // deleted -> ignored
      )
    )
    val synced = collection.mutable.Buffer.empty[String]

    for
      message <- service(db, client, synced).discover()
      discovered <- db.getAllDiscoveredGroupIds()
      cursor <- db.getMavenIndexCursor()
    yield
      discovered.map(_.groupId.value) shouldBe Seq("dev.new")
      synced.toSeq shouldBe Seq("dev.new")
      discovered.head.syncSummary shouldBe Some("Inserted 1 poms")
      discovered.head.lastSyncedAt shouldBe defined
      cursor.map(_.chainId) shouldBe Some("chain-1")
      message should include("Discovered 1")
    end for
  }

  it("does not re-surface a rejected group ID") {
    val db = new InMemoryDatabase
    val rejected = DiscoveredGroupId
      .pending(DiscoveredGroupId.Source.MavenIndex, Artifact.GroupId("dev.rejected"), Instant.now)
      .copy(status = DiscoveredGroupId.Status.Rejected)
    db.insertDiscoveredGroupIds(Seq(rejected))

    val client = new StubIndexClient(
      IndexCursor("chain-1", 50),
      Seq(Record("dev.rejected", "lib_3", "2.0.0", deleted = false))
    )
    val synced = collection.mutable.Buffer.empty[String]

    for _ <- service(db, client, synced).discover()
    yield synced shouldBe empty
  }

  it("advances the cursor only to the last chunk actually read") {
    val db = new InMemoryDatabase
    // remote is at 100, but the client only got through chunk 96 before a chunk failed
    val client = new StubIndexClient(
      IndexCursor("chain-1", 100),
      Seq(Record("dev.partial", "lib_3", "1.0.0", deleted = false)),
      reached = Some(96)
    )
    for
      _ <- service(db, client, collection.mutable.Buffer.empty[String]).discover()
      cursor <- db.getMavenIndexCursor()
    yield cursor shouldBe Some(IndexCursor("chain-1", 96))
  }

  it("keeps a group retryable when its sync fails") {
    val db = new InMemoryDatabase
    val client = new StubIndexClient(
      IndexCursor("chain-1", 10),
      Seq(Record("dev.flaky", "lib_3", "1.0.0", deleted = false))
    )
    val failing = new DiscoveryService(db, client, _ => Future.failed(new RuntimeException("503 rate limited")))
    for
      _ <- failing.discover()
      discovered <- db.getAllDiscoveredGroupIds()
      pending <- db.getPendingDiscoveredGroupIdsToSync(10)
    yield
      discovered.head.lastSyncedAt shouldBe None
      discovered.head.syncSummary.getOrElse("") should include("503")
      discovered.head.status shouldBe DiscoveredGroupId.Status.Pending
      pending.map(_.groupId.value) shouldBe Seq("dev.flaky") // still in the sync queue
  }
end DiscoveryServiceTests
