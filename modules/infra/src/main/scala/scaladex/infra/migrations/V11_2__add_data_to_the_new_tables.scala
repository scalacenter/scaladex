package scaladex.infra.migrations

import cats.effect.IO
import scaladex.infra.sql.ArtifactTable
import scaladex.infra.sql.ReleaseTable

class V11_2__add_data_to_the_new_tables extends FlywayMigration {
  override def migrationIO: IO[Unit] =
    for {
      releases <- run(ArtifactTable.getReleasesFromArtifacts.to[Seq])
      _ <- run(ReleaseTable.insertIfNotExists.updateMany(releases))
    } yield ()
}
