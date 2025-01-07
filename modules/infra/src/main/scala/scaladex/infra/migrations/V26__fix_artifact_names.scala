package scaladex.infra.migrations
import com.typesafe.scalalogging.LazyLogging
import doobie.Query0
import doobie.util.update.Update
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import scaladex.core.model.*
import scaladex.infra.sql.DoobieMappings.*
import scaladex.infra.sql.DoobieUtils.selectRequest
import scaladex.infra.sql.DoobieUtils.updateRequest

class V26__fix_artifact_names extends BaseJavaMigration with ScaladexBaseMigration with LazyLogging:
  override def migrate(context: Context): Unit =
    try
      val request =
        for
          artifactRefs <- selectArtifactRefs.to[Seq]
          _ <- updateNames.updateMany(artifactRefs.map(a => (a.name, a)))
        yield ()
      run(xa)(request).unsafeRunSync()

    catch
      case e: Throwable =>
        logger.info("failed to migrate the database")
        throw new Exception(s"failed to migrate the database because of ${e.getMessage}")

  val selectArtifactRefs: Query0[Artifact.Reference] = selectRequest(
    "artifacts",
    Seq("group_id", "artifact_id", "version"),
    where = Seq("artifact_id LIKE '%_sbt2.0.0-M2_3'")
  )

  val updateNames: Update[(Artifact.Name, Artifact.Reference)] =
    updateRequest("artifacts", Seq("artifact_name"), Seq("group_id", "artifact_id", "version"))
end V26__fix_artifact_names
