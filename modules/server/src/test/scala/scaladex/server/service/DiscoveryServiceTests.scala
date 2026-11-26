package scaladex.server.service

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scaladex.core.model.Artifact
import scaladex.core.model.DiscoveredGroupId
import scaladex.core.model.IndexCursor
import scaladex.core.model.Version
import scaladex.core.service.MavenCentralIndexClient
import scaladex.core.service.MavenCentralIndexClient.Record
import scaladex.core.service.MavenCentralIndexClient.Result
import scaladex.core.test.InMemoryDatabase
import scaladex.core.test.Values

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class DiscoveryServiceTests extends AsyncFunSpec with Matchers:
  given ExecutionContext = ExecutionContext.global

  /** Serves `chunks(n)` for chunk `n` (empty when absent), failing at `failAt` if set. */
  class StubIndexClient(
      remote: IndexCursor,
      chunks: Map[Int, Seq[Record]] = Map.empty,
      failAt: Option[Int] = None,
      seenFrom: collection.mutable.Buffer[IndexCursor] = collection.mutable.Buffer.empty
  ) extends MavenCentralIndexClient:
    def fetchRemoteCursor(): Future[IndexCursor] = Future.successful(remote)
    def recordsSince(from: IndexCursor, to: IndexCursor, maxChunks: Int)(keep: Record => Boolean): Future[Result] =
      seenFrom += from
      val requested = ((from.lastIncremental + 1) to to.lastIncremental).take(maxChunks)
      val read = requested.takeWhile(n => !failAt.contains(n))
      val records = read.flatMap(n => chunks.getOrElse(n, Nil)).filter(keep)
      Future.successful(Result(records, read.lastOption.getOrElse(from.lastIncremental)))
  end StubIndexClient

  class Recorder:
    val indexed: collection.mutable.Buffer[Artifact.Reference] = collection.mutable.Buffer.empty
    val synced: collection.mutable.Buffer[String] = collection.mutable.Buffer.empty

  private def service(
      db: InMemoryDatabase,
      client: MavenCentralIndexClient,
      recorder: Recorder = new Recorder,
      nonStandard: Set[(String, String)] = Set.empty
  ) =
    new DiscoveryService(
      db,
      client,
      refs =>
        recorder.indexed ++= refs
        Future.successful(refs.size)
      ,
      groupId =>
        recorder.synced += groupId.value
        Future.successful(s"Inserted 1 poms")
      ,
      () => nonStandard
    )

  private def ref(g: String, a: String, v: String) =
    Artifact.Reference(Artifact.GroupId(g), Artifact.ArtifactId(a), Version(v))

  it("indexes only the missing Scala artifacts and records only unknown group IDs") {
    val db = new InMemoryDatabase
    val known = Values.Scalafix.artifact // ch.epfl.scala already indexed
    db.insertArtifacts(Seq(known))

    val client = new StubIndexClient(
      IndexCursor("chain-1", 1),
      Map(
        1 -> Seq(
          Record(known.groupId.value, known.artifactId.value, known.version.value, deleted = false), // known ref
          Record(known.groupId.value, known.artifactId.value, known.version.value, deleted = false), // sources jar
          Record("ch.epfl.scala", "scalafix-core_2.13", "99.0.0", deleted = false), // known group, new version
          Record("dev.new", "lib_3", "1.0.0", deleted = false), // new group, Scala
          Record("dev.new", "lib_sjs1_3", "1.0.0", deleted = false), // same new group
          Record("com.java", "tool", "1.0.0", deleted = false), // not Scala
          Record("dev.deleted", "gone_3", "1.0.0", deleted = true) // deleted -> ignored
        )
      )
    )
    val recorder = new Recorder

    for
      message <- service(db, client, recorder).discover()
      discovered <- db.getAllDiscoveredGroupIds()
      cursor <- db.getMavenIndexCursor()
    yield
      recorder.indexed.toSet shouldBe Set(
        ref("ch.epfl.scala", "scalafix-core_2.13", "99.0.0"),
        ref("dev.new", "lib_3", "1.0.0"),
        ref("dev.new", "lib_sjs1_3", "1.0.0")
      )
      recorder.synced shouldBe empty // no listing-based sync when the index has the refs
      discovered.map(_.groupId.value) shouldBe Seq("dev.new")
      discovered.head.syncSummary shouldBe Some("Inserted 2 poms")
      discovered.head.lastSyncedAt shouldBe defined
      cursor shouldBe Some(IndexCursor("chain-1", 1))
      message should include("indexed 3 new POMs")
      message should include("discovered 1 new group IDs")
    end for
  }

  it("indexes non-standard artifacts listed in non-standard.json") {
    val db = new InMemoryDatabase
    val client = new StubIndexClient(
      IndexCursor("chain-1", 1),
      Map(
        1 -> Seq(
          Record("com.typesafe", "config", "1.5.0", deleted = false),
          Record("com.typesafe", "other-java-lib", "1.0.0", deleted = false)
        )
      )
    )
    val recorder = new Recorder
    for _ <- service(db, client, recorder, nonStandard = Set(("com.typesafe", "config"))).discover()
    yield recorder.indexed.toSeq shouldBe Seq(ref("com.typesafe", "config", "1.5.0"))
  }

  it("indexes new versions of an already-recorded group without re-discovering it") {
    val db = new InMemoryDatabase
    val alreadySynced = DiscoveredGroupId
      .pending(DiscoveredGroupId.Source.MavenIndex, Artifact.GroupId("dev.recorded"), Instant.now)
      .copy(lastSyncedAt = Some(Instant.now), syncSummary = Some("Inserted 1 poms"))
    db.insertDiscoveredGroupIds(Seq(alreadySynced))

    val client = new StubIndexClient(
      IndexCursor("chain-1", 1),
      Map(1 -> Seq(Record("dev.recorded", "lib_3", "2.0.0", deleted = false)))
    )
    val recorder = new Recorder

    for
      message <- service(db, client, recorder).discover()
      discovered <- db.getAllDiscoveredGroupIds()
    yield
      recorder.indexed.toSeq shouldBe Seq(ref("dev.recorded", "lib_3", "2.0.0"))
      recorder.synced shouldBe empty
      discovered.head.syncSummary shouldBe Some("Inserted 1 poms") // untouched
      message should include("discovered 0 new group IDs")
  }

  it("processes chunks one at a time and stops at the first failed chunk") {
    val db = new InMemoryDatabase
    db.setMavenIndexCursor(IndexCursor("chain-1", 894))
    val seenFrom = collection.mutable.Buffer.empty[IndexCursor]
    // remote is at 900, but chunk 897 fails
    val client = new StubIndexClient(
      IndexCursor("chain-1", 900),
      Map(
        895 -> Seq(Record("dev.a", "lib_3", "1.0.0", deleted = false)),
        898 -> Seq(Record("dev.after", "lib_3", "1.0.0", deleted = false))
      ),
      failAt = Some(897),
      seenFrom = seenFrom
    )
    val recorder = new Recorder
    for
      _ <- service(db, client, recorder).discover()
      cursor <- db.getMavenIndexCursor()
    yield
      seenFrom.map(_.lastIncremental).toSeq shouldBe Seq(894, 895, 896)
      cursor shouldBe Some(IndexCursor("chain-1", 896))
      recorder.indexed.map(_.groupId.value).toSeq shouldBe Seq("dev.a") // 898 is left for the next run
  }

  it("does not advance the cursor past a chunk whose artifacts failed to index") {
    val db = new InMemoryDatabase
    db.setMavenIndexCursor(IndexCursor("chain-1", 0))
    db.insertArtifacts(Seq(Values.Scalafix.artifact))
    val client = new StubIndexClient(
      IndexCursor("chain-1", 1),
      Map(1 -> Seq(Record("ch.epfl.scala", "scalafix-core_2.13", "99.0.0", deleted = false)))
    )
    val failing = new DiscoveryService(
      db,
      client,
      _ => Future.failed(new RuntimeException("db down")),
      _ => Future.successful("unused")
    )
    for
      result <- failing.discover().failed
      cursor <- db.getMavenIndexCursor()
    yield
      result.getMessage shouldBe "db down"
      cursor shouldBe Some(IndexCursor("chain-1", 0))
  }

  it("keeps a new group retryable with a full sync when indexing it fails") {
    val db = new InMemoryDatabase
    val client = new StubIndexClient(
      IndexCursor("chain-1", 1),
      Map(1 -> Seq(Record("dev.flaky", "lib_3", "1.0.0", deleted = false)))
    )
    val synced = collection.mutable.Buffer.empty[String]
    val failing = new DiscoveryService(
      db,
      client,
      _ => Future.failed(new RuntimeException("503 rate limited")),
      groupId =>
        synced += groupId.value
        Future.failed(new RuntimeException("503 rate limited"))
    )
    for
      _ <- failing.discover()
      discovered <- db.getAllDiscoveredGroupIds()
      pending <- db.getPendingDiscoveredGroupIdsToSync(10)
      cursor <- db.getMavenIndexCursor()
    yield
      synced.toSeq shouldBe Seq("dev.flaky") // retried once via the full sync in the same run
      discovered.head.lastSyncedAt shouldBe None
      discovered.head.syncSummary.getOrElse("") should include("503")
      pending.map(_.groupId.value) shouldBe Seq("dev.flaky") // still in the sync queue
      cursor shouldBe Some(IndexCursor("chain-1", 1)) // the group is queued, so the chunk is done
    end for
  }

  it("starts from chunk 0 when there is no cursor yet, and drains the whole backlog") {
    val db = new InMemoryDatabase
    val seenFrom = collection.mutable.Buffer.empty[IndexCursor]
    val client = new StubIndexClient(IndexCursor("chain-1", 3), seenFrom = seenFrom)
    for
      message <- service(db, client).discover()
      cursor <- db.getMavenIndexCursor()
    yield
      seenFrom.map(_.lastIncremental).toSeq shouldBe Seq(0, 1, 2)
      cursor shouldBe Some(IndexCursor("chain-1", 3))
      message should include("Scanned 3 chunks")
  }

  it("rewindCursor sets the cursor back by N chunks (clamped at 0)") {
    val db = new InMemoryDatabase
    val client = new StubIndexClient(IndexCursor("chain-1", 100))
    val svc = service(db, client)
    for
      _ <- svc.rewindCursor(8)
      after8 <- db.getMavenIndexCursor()
      _ <- svc.rewindCursor(500)
      afterClamp <- db.getMavenIndexCursor()
    yield
      after8 shouldBe Some(IndexCursor("chain-1", 92))
      afterClamp shouldBe Some(IndexCursor("chain-1", 0))
  }
end DiscoveryServiceTests
