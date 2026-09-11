package scaladex.data
package cleanup

import java.nio.file.*

import scala.io.Source
import scala.util.Using

import scaladex.infra.DataPaths

import io.circe.parser.*

/** A group ID submitted by the community for discovery and indexing.
  *
  * Library authors publishing via Sonatype Central Portal may find that their artifacts don't appear in Scaladex
  * because the legacy webhook doesn't fire and the scheduled backfill job only scans group IDs already in the database.
  *
  * By submitting their group ID to new-groups.json, library authors can ensure their artifacts are discovered and
  * indexed.
  *
  * @param groupId
  *   the Maven group ID (e.g., "dev.constructive")
  */
case class NewGroupId(groupId: String)

object NewGroupId:

  /** Load new group IDs from the new-groups.json file.
    *
    * The file format is a JSON array of group ID strings: ["dev.constructive", "com.example", ...]
    */
  def load(paths: DataPaths): List[NewGroupId] =
    val filePath = paths.newGroups
    if Files.exists(filePath) then
      val input = Using.resource(Source.fromFile(filePath.toFile))(_.mkString)
      decode[List[String]](input) match
        case Right(groupIds) => groupIds.map(NewGroupId.apply)
        case Left(_) => List()
    else List()
  end load
end NewGroupId
