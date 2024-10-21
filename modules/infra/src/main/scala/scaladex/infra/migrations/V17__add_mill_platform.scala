package scaladex.infra.migrations

import com.typesafe.scalalogging.LazyLogging
import doobie._
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import scaladex.core.model._
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils._

class V17__add_mill_platform extends BaseJavaMigration with ScaladexBaseMigration with LazyLogging {
  override def migrate(context: Context): Unit = {
    val migrateIO = for {
      oldArtifacts <- run(xa)(selectArtifact.to[Seq])
      (toUpdate, toDelete) = oldArtifacts.partition(a => isValidMillPlugin(a))
      _ <- run(xa)(updateNewFields.updateMany(toUpdate.map(update)))
      _ <- run(xa)(delete.updateMany(toDelete.map(_.reference)))
    } yield ()
    migrateIO.unsafeRunSync()
  }

  val selectArtifact: Query0[Artifact] =
    selectRequest("artifacts", Seq("*"), where = Seq("artifact_name LIKE '%_mill0_%'"))
  val updateNewFields: Update[(Artifact.Name, Platform, Artifact.Reference)] =
    updateRequest("artifacts", Seq("artifact_name", "platform"), Seq("group_id", "artifact_id", "version"))

  val delete: Update[Artifact.Reference] = deleteRequest("artifacts", Seq("group_id", "artifact_id", "version"))

  private def update(artifact: Artifact): (Artifact.Name, Platform, Artifact.Reference) =
    (artifact.name, artifact.platform, artifact.reference)

  private def isValidMillPlugin(artifact: Artifact): Boolean =
    artifact.platform match {
      case MillPlugin(MinorVersion(_, _)) => true
      case MillPlugin(v @ MajorVersion(_)) =>
        throw new Exception(s"Unexpected artifact with Mill version $v in ${artifact.artifactId}")
      case MillPlugin(_) => false
      case p             => throw new Exception(s"Unexpected platform $p in ${artifact.artifactId}")
    }
}
