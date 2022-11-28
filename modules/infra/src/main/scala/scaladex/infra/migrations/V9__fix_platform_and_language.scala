package scaladex.infra.migrations
import cats.effect.IO
import doobie.Query0
import doobie.util.update.Update
import scaladex.core.model.Artifact.MavenReference
import scaladex.core.model._
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils.selectRequest
import scaladex.infra.sql.DoobieUtils.updateRequest

class V9__fix_platform_and_language extends FlywayMigration {
  override def migrationIO: IO[Unit] =
    for {
      artifactToFix <- run(selectArtifact.to[Seq])
      artifactToFixWithIds = artifactToFix.flatMap(a => Artifact.ArtifactId.parse(a.artifactId).map(a -> _))
      _ <- run {
        updatePlatformAndLanguage.updateMany(artifactToFixWithIds.map {
          case (artifact, id) => (id.binaryVersion.platform, id.binaryVersion.language, artifact.mavenReference)
        })
      }
    } yield ()

  val selectArtifact: Query0[Artifact] =
    selectRequest("artifacts", Seq("*"), where = Seq("language_version = 'Java'", "version ~ '^[^.]*$'"))

  val updatePlatformAndLanguage: Update[(Platform, Language, MavenReference)] =
    updateRequest("artifacts", Seq("platform", "language_version"), Seq("group_id", "artifact_id", "version"))

}
