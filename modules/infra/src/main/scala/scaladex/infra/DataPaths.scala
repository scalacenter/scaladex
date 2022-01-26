package scaladex.infra

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import scaladex.infra.config.FilesystemConfig

/*
The contrib folder is read-only from the point of view of Scaladex. We receive PR, we merge them.
We can use GithubRepoExtractor.run() to manually set the claims.json up to date. We update them via
a PR.

scaladex-contrib
├── claims.json
├── licensesByName.json
└── non-standard.json

scaladex-credentials (optionnal)
└── search-credential
 */
object DataPaths {
  def from(config: FilesystemConfig): DataPaths =
    DataPaths(config.contrib)
}

case class DataPaths(contrib: Path) {
  assert(Files.isDirectory(contrib))

  val claims: Path = initJsonFile(contrib, "claims.json")
  val licensesByName: Path = initJsonFile(contrib, "licenses-by-name.json")
  val nonStandard: Path = initJsonFile(contrib, "non-standard.json")

  private def initJsonFile(parent: Path, name: String): Path = {
    val file = parent.resolve(name)
    if (!Files.exists(file)) {
      Files.createFile(file)
      Files.write(file, "{}".getBytes(StandardCharsets.UTF_8))
    }
    file
  }
}
