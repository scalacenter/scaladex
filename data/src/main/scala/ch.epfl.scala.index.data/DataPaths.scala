package ch.epfl.scala.index
package data

import java.nio.file.{Files, Path, Paths}

import org.slf4j.LoggerFactory

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
│   └── org/repo
├── live
|   └── projects.json
└── ivys
    ├── data.json (all the information we need about ivy releases to index)
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

sealed trait LocalRepository

object LocalRepository {
  final case object BintraySbtPlugins extends LocalRepository
}

sealed trait LocalPomRepository extends LocalRepository
object LocalPomRepository {
  final case object Bintray extends LocalPomRepository
  final case object MavenCentral extends LocalPomRepository
  final case object UserProvided extends LocalPomRepository
}

object DataPaths {

  private val log = LoggerFactory.getLogger(getClass)

  private val base = build.info.BuildInfo.baseDirectory.toPath.getParent

  private val defaultContrib = base.resolve(Paths.get("scaladex-contrib"))
  private val defaultIndex = base.resolve(Paths.get("scaladex-small-index"))
  private val defaultCredentials =
    base.resolve(Paths.get("scaladex-credentials"))

  def apply(args: List[String]): DataPaths = {
    log.info("DataPaths args: " + args)
    val (contrib, index, credentials) = args match {
      case List(contrib, index, credentials) =>
        (
          Paths.get(contrib),
          Paths.get(index),
          Paths.get(credentials)
        )
      case _ => {
        (
          defaultContrib,
          defaultIndex,
          defaultCredentials
        )
      }
    }

    new DataPaths(contrib, index, credentials, validate = true)
  }

  def fullIndex: DataPaths = {
    val index = base.resolve(Paths.get("scaladex-index"))
    new DataPaths(defaultContrib, index, defaultCredentials, validate = false)
  }

  def subIndex: DataPaths = {
    val index = base.resolve(Paths.get("scaladex-small-index"))
    new DataPaths(defaultContrib, index, defaultCredentials, validate = false)
  }
}

class DataPaths(
    private[data] val contrib: Path,
    private[data] val index: Path,
    private[data] val credentials: Path,
    validate: Boolean
) {

  private val log = LoggerFactory.getLogger(getClass)

  log.info(s"contrib folder: $contrib")
  log.info(s"index folder: $index")
  log.info(s"credentials folder: $credentials")

  def assert2(cond: Boolean) = {
    if (validate) {
      assert(cond)
    }
  }

  assert2(Files.isDirectory(contrib))
  assert2(Files.isDirectory(index))

  val claims = contrib.resolve("claims.json")
  assert2(Files.exists(claims))

  val licensesByName = contrib.resolve("licenses-by-name.json")
  assert2(Files.exists(licensesByName))

  val nonStandard = contrib.resolve("non-standard.json")
  assert2(Files.exists(nonStandard))

  // === live ===
  private val live = index.resolve("live")
  assert2(Files.isDirectory(live))

  val liveProjects = live.resolve("projects.json")
  assert2(Files.exists(liveProjects))

  // === poms ===
  private val pomsFolder = index.resolve("poms")
  assert2(Files.isDirectory(pomsFolder))

  // Bintray

  private val bintrayPom = pomsFolder.resolve("bintray")
  assert2(Files.isDirectory(bintrayPom))

  private val bintrayParentPom = bintrayPom.resolve("parent")
  assert2(Files.isDirectory(bintrayParentPom))

  private val bintrayPomSha = bintrayPom.resolve("sha")
  assert2(Files.isDirectory(bintrayPomSha))

  private val bintrayMeta = bintrayPom.resolve("meta.json")
  assert2(Files.exists(bintrayMeta))

  // MavenCentral

  private val mavenCentralPom = pomsFolder.resolve("maven-central")
  assert2(Files.isDirectory(mavenCentralPom))

  private val mavenCentralParentPom = mavenCentralPom.resolve("parent")
  assert2(Files.isDirectory(mavenCentralParentPom))

  private val mavenCentralPomSha = mavenCentralPom.resolve("sha")
  assert2(Files.isDirectory(mavenCentralPomSha))

  private val mavenCentralMeta = mavenCentralPom.resolve("meta.json")
  assert2(Files.exists(mavenCentralMeta))

  // Users

  private val usersPom = pomsFolder.resolve("users")
  assert2(Files.isDirectory(usersPom))

  private val usersParentPom = usersPom.resolve("parent")
  assert2(Files.isDirectory(usersParentPom))

  private val usersPomSha = usersPom.resolve("sha")
  assert2(Files.isDirectory(usersPomSha))

  private val usersMeta = usersPom.resolve("meta.json")
  assert2(Files.exists(usersMeta))

  // === ivys ===

  val ivys: Path = index.resolve("ivys")

  val ivysLastDownload: Path = ivys.resolve("last-download")

  val ivysData: Path = ivys.resolve("data.json")

  import LocalPomRepository._

  def poms(repository: LocalPomRepository) =
    repository match {
      case Bintray => bintrayPomSha
      case MavenCentral => mavenCentralPomSha
      case UserProvided => usersPomSha
    }

  def parentPoms(repository: LocalPomRepository) =
    repository match {
      case Bintray => bintrayParentPom
      case MavenCentral => mavenCentralParentPom
      case UserProvided => usersParentPom
    }

  def meta(repository: LocalPomRepository) =
    repository match {
      case Bintray => bintrayMeta
      case MavenCentral => mavenCentralMeta
      case UserProvided => usersMeta
    }

  val github = index.resolve("github")

  val movedGithub = github.resolve("moved.json")
}
