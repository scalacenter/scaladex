package scaladex.infra.migrations
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.Query0
import doobie.util.update.Update
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import scaladex.core.model.Artifact
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils._

class V13_2__update_new_fields_in_artifacts extends BaseJavaMigration with ScaladexBaseMigration with LazyLogging {
  override def migrate(context: Context): Unit =
    try {
      (for {
        oldArtifacts <- run(xa)(selectArtifact.to[Seq])
        groupedArtifacts = oldArtifacts.grouped(10000).toSeq
        _ <- groupedArtifacts
          .map(artifacts => run(xa)(updateNewFields.updateMany(artifacts.map(update))))
          .sequence
      } yield ())
        .unsafeRunSync()

    } catch {
      case e: Throwable =>
        logger.info("failed to migrate the database")
        throw new Exception(s"failed to migrate the database because of ${e.getMessage}")
    }

  val selectArtifact: Query0[Artifact] = selectRequest("artifacts", Seq("*"))
  val updateNewFields: Update[(Boolean, Boolean, Artifact.Reference)] =
    updateRequest("artifacts", Seq("is_semantic", "is_prerelease"), Seq("group_id", "artifact_id", "version"))

  private def update(artifact: Artifact): (Boolean, Boolean, Artifact.Reference) =
    (artifact.version.isSemantic, artifact.version.isPreRelease, artifact.reference)
}
