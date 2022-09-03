package scaladex.infra.migrations

import com.typesafe.scalalogging.LazyLogging
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import scaladex.infra.sql.ArtifactTable
import scaladex.infra.sql.ReleaseTable

class V11_2__add_data_to_the_new_tables extends BaseJavaMigration with ScaladexBaseMigration with LazyLogging {
  override def migrate(context: Context): Unit =
    try {
      (for {
        releases <- run(xa)(ArtifactTable.getReleasesFromArtifacts.to[Seq])
        _ <- run(xa)(ReleaseTable.insertIfNotExists.updateMany(releases))
      } yield ())
        .unsafeRunSync()

    } catch {
      case e: Throwable =>
        logger.info("failed to migrate the database")
        throw new Exception(s"failed to migrate the database because of ${e.getMessage}")
    }

}
