package scaladex.infra.migrations
import com.typesafe.scalalogging.LazyLogging
import doobie.Query0
import doobie.util.update.Update
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import scaladex.core.model._
import scaladex.infra.sql.DoobieMappings._
import scaladex.infra.sql.DoobieUtils.selectRequest
import scaladex.infra.sql.DoobieUtils.updateRequest

class V9__fix_platform_and_language extends BaseJavaMigration with ScaladexBaseMigration with LazyLogging {
  override def migrate(context: Context): Unit =
    try {
      val request =
        for {
          artifacts0 <- run(xa)(selectArtifact.to[Seq])
          artifacts1 = artifacts0.map(a => (a.platform, a.language, a.reference))
          _ <- run(xa)(updatePlatformAndLanguage.updateMany(artifacts1))
        } yield ()
      request.unsafeRunSync()

    } catch {
      case e: Throwable =>
        logger.info("failed to migrate the database")
        throw new Exception(s"failed to migrate the database because of ${e.getMessage}")
    }

  val selectArtifact: Query0[Artifact] =
    selectRequest("artifacts", Seq("*"), where = Seq("language_version = 'Java'", "version ~ '^[^.]*$'"))

  val updatePlatformAndLanguage: Update[(Platform, Language, Artifact.Reference)] =
    updateRequest("artifacts", Seq("platform", "language_version"), Seq("group_id", "artifact_id", "version"))

}
