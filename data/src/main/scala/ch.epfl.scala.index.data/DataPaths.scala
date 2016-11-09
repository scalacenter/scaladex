package ch.epfl.scala.index
package data

import java.nio.file.{Paths, Files}

/*
index
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
└── live
    └── projects.json

contrib
├── claims.json
├── licensesByName.json
└── non-standard.json
 */

sealed trait LocalRepository
object LocalRepository {
  final case object Bintray extends LocalRepository
  final case object MavenCentral extends LocalRepository
  final case object UserProvided extends LocalRepository
}
 
// List("/home/gui/center/scaladex/contrib", "/home/gui/center/scaladex/index")
object DataPaths {
  def apply(args: List[String]): DataPaths = new DataPaths(args)
}

class DataPaths(private[DataPaths] args: List[String]) {

  private val (contrib, index) = args.toList match {
    case List(contrib, index) => (Paths.get(contrib), Paths.get(index))
    case _ => {
      val base = build.info.BuildInfo.baseDirectory.toPath
      (base.resolve(Paths.get("contrib")), base.resolve(Paths.get("index")))
    }
  }
 
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

  import LocalRepository._

  def poms(repository: LocalRepository) =
    repository match {
      case Bintray => bintrayPomSha
      case MavenCentral => mavenCentralPomSha
      case UserProvided => usersPomSha
    }

  def parentPoms(repository: LocalRepository) =
    repository match {
      case Bintray => bintrayParentPom
      case MavenCentral => mavenCentralParentPom
      case UserProvided => usersParentPom
    }

  def meta(repository: LocalRepository) =
    repository match {
      case Bintray => bintrayMeta
      case MavenCentral => mavenCentralMeta
      case UserProvided => usersMeta
    }

  val github = index.resolve("github")
}
