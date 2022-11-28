package scaladex.infra.migrations
import cats.effect.IO
import cats.implicits._
import doobie.Query0
import doobie.util.update.Update
import scaladex.core.model.Artifact
import scaladex.core.model.Artifact.MavenReference
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils.selectRequest
import scaladex.infra.sql.DoobieUtils.updateRequest

class V13_2__update_new_fields_in_artifacts extends FlywayMigration {
  override def migrationIO: IO[Unit] =
    for {
      oldArtifacts <- run(selectArtifact.to[Seq])
      groupedArtifacts = oldArtifacts.grouped(10000).toSeq
      _ <- groupedArtifacts
        .map(artifacts => run(updateNewFields.updateMany(artifacts.map(update))))
        .sequence
    } yield ()

  private val selectArtifact: Query0[Artifact] = selectRequest("artifacts", Seq("*"))
  private val updateNewFields: Update[(Boolean, Boolean, MavenReference)] =
    updateRequest("artifacts", Seq("is_semantic", "is_prerelease"), Seq("group_id", "artifact_id", "version"))

  private def update(artifact: Artifact): (Boolean, Boolean, MavenReference) =
    (artifact.version.isSemantic, artifact.version.isPreRelease, artifact.mavenReference)
}
