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

class V25__fix_sbt_platform extends BaseJavaMigration with ScaladexBaseMigration with LazyLogging {
  override def migrate(context: Context): Unit =
    try {
      val request =
        for {
          artifactRefs <- selectArtifactRefs.to[Seq]
          _ <- updatePlatformAndLanguage.updateMany(artifactRefs.map(a => (a.platform, a.language, a)))
        } yield ()
      run(xa)(request).unsafeRunSync()

    } catch {
      case e: Throwable =>
        logger.info("failed to migrate the database")
        throw new Exception(s"failed to migrate the database because of ${e.getMessage}")
    }

  val selectArtifactRefs: Query0[Artifact.Reference] = selectRequest(
    "artifacts",
    Seq("group_id", "artifact_id", "version"),
    where = Seq("platform='sbt1.0' OR artifact_id LIKE '%_sbt2.0.0-M2_3'")
  )

  val updatePlatformAndLanguage: Update[(Platform, Language, Artifact.Reference)] =
    updateRequest("artifacts", Seq("platform", "language_version"), Seq("group_id", "artifact_id", "version"))

}
