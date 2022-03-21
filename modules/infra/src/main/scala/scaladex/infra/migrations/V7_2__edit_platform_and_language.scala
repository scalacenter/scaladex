package scaladex.infra.migrations

import java.time.Instant

import scala.concurrent.ExecutionContext

import cats.effect.ContextShift
import cats.effect.IO
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.Query0
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.util.update.Update
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import scaladex.core.model.Artifact.MavenReference
import scaladex.core.model._
import scaladex.infra.config.PostgreSQLConfig
import scaladex.infra.sql.DoobieUtils.Mappings._
import scaladex.infra.sql.DoobieUtils.selectRequest
import scaladex.infra.sql.DoobieUtils.updateRequest

class V7_2__edit_platform_and_language extends BaseJavaMigration with LazyLogging {
  import V7_2__edit_platform_and_language._

  private implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  override def migrate(context: Context): Unit =
    try {
      val config = PostgreSQLConfig.load().get
      val xa =
        Transactor
          .fromDriverManager[IO](
            config.driver,
            config.url,
            config.user,
            config.pass.decode
          )

      (for {
        oldArtifacts <- run(xa)(selectArtifact.to[Seq])
        groupedArtifacts = oldArtifacts.grouped(10000).toSeq
        numberOfArtifactsUpdated <- groupedArtifacts
          .map(artifacts => run(xa)(updatePlatformAndLanguage.updateMany(artifacts.map(_.update))))
          .sequence
        _ = logger.info(s"Updated ${numberOfArtifactsUpdated.sum} artifacts")
        _ <- run(xa)(sql"ALTER TABLE artifacts DROP COLUMN binary_version".update.run)
        _ = logger.info(s"column binary_version deleted")
      } yield ())
        .unsafeRunSync()

    } catch {
      case e: Throwable =>
        logger.info("failed to migrate the database")
        throw new Exception(s"failed to migrate the database because of ${e.getMessage}")
    }

  val selectArtifact: Query0[OldArtifact] = selectRequest("artifacts", Seq("*"))

  val updatePlatformAndLanguage: Update[(Platform, Language, MavenReference)] =
    updateRequest("artifacts", Seq("platform", "language_version"), Seq("group_id", "artifact_id", "version"))

  private def run[A](xa: doobie.Transactor[IO])(v: doobie.ConnectionIO[A]): IO[A] =
    v.transact(xa)
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
