package scaladex.infra.migrations
import com.typesafe.scalalogging.LazyLogging
import doobie.Query0
import doobie.util.update.Update
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import scaladex.core.model.Artifact.MavenReference
import scaladex.core.model._
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils.selectRequest
import scaladex.infra.sql.DoobieUtils.updateRequest

class V9__fix_platform_and_language extends BaseJavaMigration with ScaladexBaseMigration with LazyLogging {
  override def migrate(context: Context): Unit =
    try {
      (for {
        artifactToFix <- run(xa)(selectArtifact.to[Seq])
        artifactToFixWithIds = artifactToFix.flatMap(a => Artifact.ArtifactId.parse(a.artifactId).map(a -> _))
        numberOfArtifactsUpdated <- run(xa) {
          updatePlatformAndLanguage.updateMany(artifactToFixWithIds.map {
            case (artifact, id) => (id.binaryVersion.platform, id.binaryVersion.language, artifact.mavenReference)
          })
        }
        _ = logger.info(s"Updated $numberOfArtifactsUpdated artifacts")
      } yield ())
        .unsafeRunSync()

    } catch {
      case e: Throwable =>
        logger.info("failed to migrate the database")
        throw new Exception(s"failed to migrate the database because of ${e.getMessage}")
    }

  val selectArtifact: Query0[Artifact] =
    selectRequest("artifacts", "*", where = Some("language_version = 'Java' and version ~ '^[^.]*$'"))

  val updatePlatformAndLanguage: Update[(Platform, Language, MavenReference)] =
    updateRequest("artifacts", Seq("platform", "language_version"), Seq("group_id", "artifact_id", "version"))

}
