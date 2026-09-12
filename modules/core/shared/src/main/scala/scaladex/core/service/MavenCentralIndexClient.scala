package scaladex.core.service

import scala.concurrent.Future

import scaladex.core.model.IndexCursor

/** Reads the Maven Central "nexus" published index (`https://repo1.maven.org/maven2/.index/`). See
  * `doc/dev/maven-central-discovery.md` §2.5.
  */
trait MavenCentralIndexClient:
  /** Parse `nexus-maven-repository-index.properties`. */
  def fetchRemoteCursor(): Future[IndexCursor]

  /** Stream the records of the incremental chunks in `(from.lastIncremental, to.lastIncremental]`, capped at
    * `maxChunks` per call, keeping only the records for which `keep` is true (a full chunk holds ~2M records, so
    * filtering during the scan avoids materialising them all). Processing stops at the first chunk that fails to
    * download or parse, and `Result.reachedIncremental` reports the last chunk fully processed so the caller advances
    * its cursor no further.
    */
  def recordsSince(from: IndexCursor, to: IndexCursor, maxChunks: Int)(
      keep: MavenCentralIndexClient.Record => Boolean
  ): Future[MavenCentralIndexClient.Result]
end MavenCentralIndexClient

object MavenCentralIndexClient:
  final case class Record(groupId: String, artifactId: String, version: String, deleted: Boolean)
  final case class Result(records: Seq[Record], reachedIncremental: Int)
