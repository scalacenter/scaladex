package scaladex.infra.migrations

import java.time.Instant

import cats.implicits.*
import com.typesafe.scalalogging.LazyLogging
import doobie.Query0
import doobie.implicits.*
import doobie.util.update.Update
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import scaladex.core.model.*
import scaladex.infra.sql.DoobieMappings.*
import scaladex.infra.sql.DoobieUtils.selectRequest
import scaladex.infra.sql.DoobieUtils.updateRequest

class V7_2__edit_platform_and_language extends BaseJavaMigration with ScaladexBaseMigration with LazyLogging:

  import V7_2__edit_platform_and_language.*

  override def migrate(context: Context): Unit =
    try
      (for
        oldArtifacts <- run(xa)(selectArtifact.to[Seq])
        groupedArtifacts = oldArtifacts.grouped(10000).toSeq
        _ <- groupedArtifacts
          .map(artifacts => run(xa)(updatePlatformAndLanguage.updateMany(artifacts.map(_.update))))
          .sequence
        _ <- run(xa)(sql"ALTER TABLE artifacts DROP COLUMN binary_version".update.run)
      yield ())
        .unsafeRunSync()

    catch
      case e: Throwable =>
        logger.info("failed to migrate the database")
        throw new Exception(s"failed to migrate the database because of ${e.getMessage}")

  val selectArtifact: Query0[OldArtifact] = selectRequest("artifacts", Seq("*"))

  val updatePlatformAndLanguage: Update[(Platform, Language, Artifact.Reference)] =
    updateRequest("artifacts", Seq("platform", "language_version"), Seq("group_id", "artifact_id", "version"))
end V7_2__edit_platform_and_language

object V7_2__edit_platform_and_language:
  case class OldArtifact(
      groupId: Artifact.GroupId,
      artifactId: String,
      version: Version,
      artifactName: Artifact.Name,
      binaryVersion: BinaryVersion,
      projectRef: Project.Reference,
      description: Option[String],
      releaseDate: Option[Instant],
      resolver: Option[Resolver],
      licenses: Set[License],
      isNonStandardLib: Boolean
  ):
    def update: (Platform, Language, Artifact.Reference) =
      val mavenRef = Artifact.Reference(groupId, Artifact.ArtifactId(artifactId), version)
      (binaryVersion.platform, binaryVersion.language, mavenRef)
  end OldArtifact
end V7_2__edit_platform_and_language
