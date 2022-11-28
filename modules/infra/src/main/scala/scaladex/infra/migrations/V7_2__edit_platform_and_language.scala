package scaladex.infra.migrations

import java.time.Instant

import cats.effect.IO
import cats.implicits._
import doobie.Query0
import doobie.implicits._
import doobie.util.update.Update
import scaladex.core.model.Artifact.MavenReference
import scaladex.core.model._
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils.selectRequest
import scaladex.infra.sql.DoobieUtils.updateRequest

class V7_2__edit_platform_and_language extends FlywayMigration {

  import V7_2__edit_platform_and_language._

  override def migrationIO: IO[Unit] =
    for {
      oldArtifacts <- run(selectArtifact.to[Seq])
      groupedArtifacts = oldArtifacts.grouped(10000).toSeq
      _ <- groupedArtifacts
        .map(artifacts => run(updatePlatformAndLanguage.updateMany(artifacts.map(_.update))))
        .sequence
      _ <- run(sql"ALTER TABLE artifacts DROP COLUMN binary_version".update.run)
    } yield ()

  val selectArtifact: Query0[OldArtifact] = selectRequest("artifacts", Seq("*"))

  val updatePlatformAndLanguage: Update[(Platform, Language, MavenReference)] =
    updateRequest("artifacts", Seq("platform", "language_version"), Seq("group_id", "artifact_id", "version"))

}
object V7_2__edit_platform_and_language {
  case class OldArtifact(
      groupId: Artifact.GroupId,
      artifactId: String,
      version: SemanticVersion,
      artifactName: Artifact.Name,
      binaryVersion: BinaryVersion,
      projectRef: Project.Reference,
      description: Option[String],
      releaseDate: Option[Instant],
      resolver: Option[Resolver],
      licenses: Set[License],
      isNonStandardLib: Boolean
  ) {
    def update: (Platform, Language, MavenReference) = {
      val mavenRef = MavenReference(groupId = groupId.value, artifactId = artifactId, version = version.toString)
      (binaryVersion.platform, binaryVersion.language, mavenRef)
    }
  }
}
