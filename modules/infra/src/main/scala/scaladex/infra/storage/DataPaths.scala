package scaladex.infra.storage

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.slf4j.LoggerFactory
import scaladex.core.model.data.LocalPomRepository
import scaladex.core.model.data.LocalPomRepository._
import scaladex.infra.config.FilesystemConfig

/*
The contrib folder is read-only from the point of view of Scaladex. We receive PR, we merge them.
We can use GithubRepoExtractor.run() to manually set the claims.json up to date. We update them via
a PR.

The index folder is write-only. We don't accept PR. Users have to login on Scaladex and update via
the UI.

scaladex-small-index or scaladex-index
├── poms
│   ├── bintray
│   │   ├── meta.json
│   │   ├── parent
│   │   │   ├── /com/example/foo_2.11/0.1.0/foo_2.11-0.1.0.pom
│   │   │   └── /org/example/bar.2_11/0.2.0/bar.2_11-0.2.0.pom
│   │   └── sha
│   │       ├── 00005ff16784724f7dd0a225cfb2483236bd78f2.pom
│   │       └── 00009519ce4c69d77f9070a207b9602a90b809b0.pom
│   ├── maven-central
│   │   ├── meta.json
│   │   ├── parents
│   │   └── sha
│   └── users
│       ├── meta.json
│       ├── parents
│       └── sha
├── github
│   ├── moved.json
│   ├── org/repo
│   ├── ...
│   └── org/repo
├── live
|   └── projects.json
└── ivys
    ├── data.json (all the information we need about ivy artifacts to index)
    ├── last-download (date of the last time we fetched information from bintray)
    └── subject (e.g. “sbt”)
        └── repo (e.g. “sbt-plugin-releases”)
            └── ivy files (e.g. “com.github.gseitz/sbt-release/scala_2.10/sbt_0.13/0.8.5/ivys/ivy.xml”)
scaladex-contrib
├── claims.json
├── licensesByName.json
└── non-standard.json

scaladex-credentials (optionnal)
└── search-credential
 */
object DataPaths {
  def from(config: FilesystemConfig): DataPaths =
    DataPaths(config.contrib, config.index, config.credentials, validate = true)
}

case class DataPaths(contrib: Path, index: Path, credentials: Path, validate: Boolean) {

  private val log = LoggerFactory.getLogger(getClass)

  log.info(s"contrib folder: $contrib")
  log.info(s"index folder: $index")
  log.info(s"credentials folder: $credentials")

  assert2(Files.isDirectory(contrib))
  assert2(Files.isDirectory(index))

  val claims: Path = initJsonFile(contrib, "claims.json")
  val licensesByName: Path = initJsonFile(contrib, "licenses-by-name.json")
  val nonStandard: Path = initJsonFile(contrib, "non-standard.json")

  // === poms ===
  private val pomsFolder = initDirectory(index, "poms")

  // Bintray
  private val bintrayPom = initDirectory(pomsFolder, "bintray")
  private val bintrayPomSha = initDirectory(bintrayPom, "sha")
  private val bintrayMeta = initFile(bintrayPom, "meta.json")

  // MavenCentral

  private val mavenCentralPom = initDirectory(pomsFolder, "maven-central")
  private val mavenCentralPomSha = initDirectory(mavenCentralPom, "sha")
  private val mavenCentralMeta = initFile(mavenCentralPom, "meta.json")

  // Users

  private val usersPom = initDirectory(pomsFolder, "users")
  private val usersPomSha = initDirectory(usersPom, "sha")
  private val usersMeta = initFile(usersPom, "meta.json")

  // === ivys ===

  val ivys: Path = index.resolve("ivys")

  val ivysLastDownload: Path = ivys.resolve("last-download")

  val ivysData: Path = ivys.resolve("data.json")

  def poms(repository: LocalPomRepository): Path =
    repository match {
      case Bintray      => bintrayPomSha
      case MavenCentral => mavenCentralPomSha
      case UserProvided => usersPomSha
    }

  def meta(repository: LocalPomRepository): Path =
    repository match {
      case Bintray      => bintrayMeta
      case MavenCentral => mavenCentralMeta
      case UserProvided => usersMeta
    }

  val github: Path = index.resolve("github")

  val movedGithub: Path = github.resolve("moved.json")

  def fullIndex: DataPaths = {
    val fullIndex = index.getParent.resolve(Paths.get("scaladex-index"))
    copy(index = fullIndex, validate = false)
  }

  def subIndex: DataPaths = {
    val subIndex = index.getParent.resolve(Paths.get("scaladex-small-index"))
    copy(index = subIndex, validate = false)
  }

  private def assert2(cond: Boolean): Unit =
    if (validate) assert(cond)

  private def initFile(parent: Path, name: String): Path = {
    val file = parent.resolve(name)
    if (!Files.exists(file)) Files.createFile(file)
    file
  }

  private def initJsonFile(parent: Path, name: String): Path = {
    val file = parent.resolve(name)
    if (!Files.exists(file)) {
      Files.createFile(file)
      Files.write(file, "{}".getBytes(StandardCharsets.UTF_8))
    }
    file
  }

  private def initDirectory(parent: Path, name: String): Path = {
    val dir = parent.resolve(name)
    if (!Files.exists(dir)) Files.createDirectory(dir)
    dir
  }
}
