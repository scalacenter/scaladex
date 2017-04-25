package ch.epfl.scala.index
package data

import java.nio.file.{Files, Path, Paths}

/*
The contrib folder is read-only from the point of view of Scaladex. We receive PR, we merge them.
We can use GithubRepoExtractor.run() to manually set the claims.json up to date. We update them via
a PR.

The index folder is write-only. We don't accept PR. Users have to login on Scaladex and update via
the UI.

scaladex-index
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
│   ├── org/repo
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

scaladex-credential (optionnal)
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
  def apply(args: List[String]): DataPaths = new DataPaths(args)
}

class DataPaths(private[DataPaths] val args: List[String]) {

  println("DataPaths args: " + args)

  private[data] val (contrib, index, credentials) = args match {
    case List(contrib, index, credentials) =>
      (
        Paths.get(contrib),
        Paths.get(index),
        Paths.get(credentials)
      )
    case _ => {
      val base = build.info.BuildInfo.baseDirectory.toPath.getParent

      (
        base.resolve(Paths.get("scaladex-contrib")),
        base.resolve(Paths.get("scaladex-index")),
        base.resolve(Paths.get("scaladex-credentials"))
      )
    }
  }

  println(s"contrib folder: $contrib")
  println(s"index folder: $index")
  println(s"credentials folder: $credentials")

  assert(Files.isDirectory(contrib))
  assert(Files.isDirectory(index))

  val claims = contrib.resolve("claims.json")
  assert(Files.exists(claims))

  val licensesByName = contrib.resolve("licenses-by-name.json")
  assert(Files.exists(licensesByName))

  val nonStandard = contrib.resolve("non-standard.json")
  assert(Files.exists(nonStandard))

  // === live ===
  private val live = index.resolve("live")
  assert(Files.isDirectory(live))

  val liveProjects = live.resolve("projects.json")
  assert(Files.exists(liveProjects))

  // === poms ===
  private val pomsFolder = index.resolve("poms")
  assert(Files.isDirectory(pomsFolder))

  // Bintray

  private val bintrayPom = pomsFolder.resolve("bintray")
  assert(Files.isDirectory(bintrayPom))

  private val bintrayParentPom = bintrayPom.resolve("parent")
  assert(Files.isDirectory(bintrayParentPom))

  private val bintrayPomSha = bintrayPom.resolve("sha")
  assert(Files.isDirectory(bintrayPomSha))

  private val bintrayMeta = bintrayPom.resolve("meta.json")
  assert(Files.exists(bintrayMeta))

  // MavenCentral

  private val mavenCentralPom = pomsFolder.resolve("maven-central")
  assert(Files.isDirectory(mavenCentralPom))

  private val mavenCentralParentPom = mavenCentralPom.resolve("parent")
  assert(Files.isDirectory(mavenCentralParentPom))

  private val mavenCentralPomSha = mavenCentralPom.resolve("sha")
  assert(Files.isDirectory(mavenCentralPomSha))

  private val mavenCentralMeta = mavenCentralPom.resolve("meta.json")
  assert(Files.exists(mavenCentralMeta))

  // Users

  private val usersPom = pomsFolder.resolve("users")
  assert(Files.isDirectory(usersPom))

  private val usersParentPom = usersPom.resolve("parent")
  assert(Files.isDirectory(usersParentPom))

  private val usersPomSha = usersPom.resolve("sha")
  assert(Files.isDirectory(usersPomSha))

  private val usersMeta = usersPom.resolve("meta.json")
  assert(Files.exists(usersMeta))

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
}
