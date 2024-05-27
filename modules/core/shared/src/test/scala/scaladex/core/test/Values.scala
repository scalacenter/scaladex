package scaladex.core.test

import java.time.Instant
import java.time.temporal.ChronoUnit

import scaladex.core.model.Artifact
import scaladex.core.model.Artifact._
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.ArtifactDependency.Scope
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Category
import scaladex.core.model.GithubCommitActivity
import scaladex.core.model.GithubContributor
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubIssue
import scaladex.core.model.GithubStatus
import scaladex.core.model.Jvm
import scaladex.core.model.License
import scaladex.core.model.MajorVersion
import scaladex.core.model.PatchVersion
import scaladex.core.model.Project
import scaladex.core.model.Project.Settings
import scaladex.core.model.Scala
import scaladex.core.model.ScalaJs
import scaladex.core.model.ScalaNative
import scaladex.core.model.SemanticVersion
import scaladex.core.model.Url
import scaladex.core.model.search.ProjectDocument

object Values {
  val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  val ok: GithubStatus = GithubStatus.Ok(now)

  val `2.6.1` = PatchVersion(2, 6, 1)
  val `4`: SemanticVersion = MajorVersion(4)
  val `2.7.0` = PatchVersion(2, 7, 0)
  val `7.0.0` = PatchVersion(7, 0, 0)
  val `7.1.0` = PatchVersion(7, 1, 0)
  val `7.2.0-PREVIEW.1` = SemanticVersion.parse("7.2.0-PREVIEW.1").get
  val `7.2.0-PREVIEW.2` = SemanticVersion.parse("7.2.0-PREVIEW.2").get
  val `7.2.0` = PatchVersion(7, 2, 0)
  val `7.3.0` = PatchVersion(7, 3, 0)

  val `_2.13`: BinaryVersion = BinaryVersion(Jvm, Scala.`2.13`)
  val `_3`: BinaryVersion = BinaryVersion(Jvm, Scala.`3`)
  val `_sjs1_3`: BinaryVersion = BinaryVersion(ScalaJs.`1.x`, Scala.`3`)
  val `_sjs0.6_2.13` = BinaryVersion(ScalaJs.`0.6`, Scala.`2.13`)
  val `_native0.4_2.13` = BinaryVersion(ScalaNative.`0.4`, Scala.`2.13`)

  object Scalafix {
    val reference: Project.Reference = Project.Reference.from("scalacenter", "scalafix")
    val creationDate: Instant = Instant.ofEpochMilli(1475505237265L)
    val artifactId: ArtifactId = ArtifactId.parse("scalafix-core_2.13").get
    val artifact: Artifact = Artifact(
      groupId = GroupId("ch.epfl.scala"),
      artifactId = artifactId.value,
      version = SemanticVersion.parse("0.9.30").get,
      artifactName = artifactId.name,
      platform = artifactId.binaryVersion.platform,
      language = artifactId.binaryVersion.language,
      projectRef = reference,
      description = None,
      releaseDate = creationDate,
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false,
      fullScalaVersion = None
    )
    val githubInfo: GithubInfo =
      GithubInfo.empty
        .copy(
          stars = Some(643),
          forks = Some(148),
          topics = Set("refactoring", "dotty", "linter", "metaprogramming", "scalafix", "sbt", "rewrite", "scala"),
          contributors = Seq(contributor("olafurpg"), contributor("scala-steward"))
        )
    val settings: Settings = Settings(
      preferStableVersion = false,
      defaultArtifact = None,
      customScalaDoc = None,
      documentationLinks = List(),
      contributorsWanted = false,
      deprecatedArtifacts = Set(),
      cliArtifacts = Set(),
      category = Some(Category.LintingAndRefactoring),
      chatroom = None
    )
    val project: Project =
      Project.default(reference, None, Some(githubInfo), Some(settings), now = now)

    val projectDocument: ProjectDocument =
      ProjectDocument(project.copy(creationDate = Some(now.minus(1, ChronoUnit.MINUTES))), Seq(artifact), 0, Seq.empty)
  }

  object PlayJsonExtra {
    val reference: Project.Reference = Project.Reference.from("xuwei-k", "play-json-extra")
    val creationDate: Instant = Instant.ofEpochMilli(1411736618000L)
    val artifactId: ArtifactId = ArtifactId.parse("play-json-extra_2.11").get
    val artifact: Artifact = Artifact(
      groupId = GroupId("com.github.xuwei-k"),
      artifactId = artifactId.value,
      version = SemanticVersion.parse("0.1.1-play2.3-M1").get,
      artifactName = artifactId.name,
      platform = artifactId.binaryVersion.platform,
      language = artifactId.binaryVersion.language,
      projectRef = reference,
      description = None,
      releaseDate = creationDate,
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false,
      fullScalaVersion = None
    )
    val dependency: ArtifactDependency =
      ArtifactDependency(
        source = Cats.`core_3:2.6.1`.mavenReference,
        target = artifact.mavenReference,
        Scope("compile")
      )
    val githubInfo: GithubInfo = GithubInfo.empty
    val settings: Project.Settings = Project.Settings.empty.copy(
      defaultArtifact = Some(artifact.artifactName),
      category = Some(Category.Json)
    )
  }

  object Cats {
    val reference: Project.Reference = Project.Reference.from("typelevel", "cats")
    val issueAboutFoo: GithubIssue =
      GithubIssue(1, "Issue about foo", Url("https://github.com/typelevel/cats/pull/1"))
    val issueAboutBar: GithubIssue =
      GithubIssue(2, "Issue about bar", Url("https://github.com/typelevel/cats/pull/2"))
    val githubInfo: GithubInfo = GithubInfo.empty
      .copy(
        stars = Some(4337),
        forks = Some(1081),
        contributingGuide = Some(Url("https://github.com/typelevel/cats/blob/main/CONTRIBUTING.md")),
        openIssues = List(issueAboutFoo, issueAboutBar),
        contributors = Seq(contributor("travisbrown"), contributor("ceedub"), contributor("kailuowang")),
        commitActivity = Seq(GithubCommitActivity(25, Instant.now, IndexedSeq(0, 3, 4, 0, 5, 6, 7)))
      )
    val project: Project = Project.default(
      reference,
      githubInfo = Some(githubInfo),
      now = now
    )
    val groupId: GroupId = GroupId("org.typelevel")
    val license: License = License("MIT License", "MIT", Some("https://spdx.org/licenses/MIT.html"))

    private def getArtifact(
        name: String,
        binaryVersion: BinaryVersion,
        version: SemanticVersion,
        description: Option[String] = None,
        fullScalaVersion: Option[SemanticVersion] = None
    ): Artifact = {
      val artifactId = ArtifactId(Name(name), binaryVersion)
      Artifact(
        groupId = groupId,
        artifactId = artifactId.value,
        version = version,
        artifactName = artifactId.name,
        platform = binaryVersion.platform,
        language = binaryVersion.language,
        projectRef = reference,
        description = description,
        releaseDate = Instant.ofEpochMilli(1620911032000L),
        resolver = None,
        licenses = Set(license),
        isNonStandardLib = false,
        fullScalaVersion = fullScalaVersion
      )
    }

    val `core_3:2.6.1`: Artifact = getArtifact(
      "cats-core",
      `_3`,
      `2.6.1`,
      description = Some("Cats core"),
      fullScalaVersion = SemanticVersion.parse("3.0.0")
    )
    val `core_2.13:2.6.1`: Artifact = getArtifact("cats-core", `_2.13`, `2.6.1`, description = Some("Cats core"))
    val `core_3:4`: Artifact = getArtifact("cats-core", `_3`, `4`, description = Some("Cats core"))
    val `core_3:2.7.0`: Artifact = getArtifact(
      "cats-core",
      `_3`,
      `2.7.0`,
      description = Some("Cats core"),
      fullScalaVersion = SemanticVersion.parse("3.0.2")
    )

    val `core_sjs1_3:2.6.1`: Artifact = getArtifact("cats-core", `_sjs1_3`, `2.6.1`, description = Some("Cats core"))
    val `core_sjs06_2.13:2.6.1`: Artifact =
      getArtifact("cats-core", `_sjs0.6_2.13`, `2.6.1`, description = Some("Cats core"))
    val `core_native04_2.13:2.6.1`: Artifact =
      getArtifact("cats-core", `_native0.4_2.13`, `2.6.1`, description = Some("Cats core"))

    val `kernel_2.13`: Artifact = getArtifact("cats-kernel", `_2.13`, `2.6.1`)
    val `kernel_3:2.6.1`: Artifact = getArtifact("cats-kernel", `_3`, `2.6.1`)
    val `laws_3:2.6.1`: Artifact = getArtifact("cats-laws", `_3`, `2.6.1`)

    val allArtifacts: Seq[Artifact] =
      Seq(
        `core_3:2.6.1`,
        `core_3:2.7.0`,
        `core_sjs1_3:2.6.1`,
        `core_sjs06_2.13:2.6.1`,
        `core_native04_2.13:2.6.1`,
        `kernel_3:2.6.1`,
        `laws_3:2.6.1`
      )

    val dependencies: Seq[ArtifactDependency] = Seq(
      ArtifactDependency(
        source = `core_3:2.6.1`.mavenReference,
        target = `kernel_3:2.6.1`.mavenReference,
        Scope("compile")
      ),
      ArtifactDependency(
        source = `core_3:2.6.1`.mavenReference,
        target = `laws_3:2.6.1`.mavenReference,
        Scope("compile")
      ),
      ArtifactDependency(
        source = `core_3:2.6.1`.mavenReference,
        target = MavenReference(
          "com.gu",
          "ztmp-scala-automation_2.10",
          "1.9"
        ), // dependency with a corresponding getArtifact
        Scope("compile")
      )
    )

    val projectDocument: ProjectDocument =
      ProjectDocument(project.copy(creationDate = Some(now.minus(10, ChronoUnit.MINUTES))), allArtifacts, 1, Seq.empty)
  }

  object CatsEffect {
    val dependency: ArtifactDependency = ArtifactDependency(
      source = MavenReference(
        "cats-effect",
        "cats-effect-kernel_3",
        "3.2.3"
      ),
      target = Cats.`core_3:2.6.1`.mavenReference,
      Scope("compile")
    )

    val testDependency: ArtifactDependency = ArtifactDependency(
      source = MavenReference(
        "cats-effect",
        "cats-effect-kernel_3",
        "3.2.3"
      ),
      target = MavenReference("typelevel", "scalacheck_3", "1.15.4"),
      Scope("test")
    )
  }

  object SbtCrossProject {
    val reference: Project.Reference = Project.Reference.from("portable-scala", "sbt-crossproject")
    val mavenReference: MavenReference =
      MavenReference("org.portable-scala", "sbt-scalajs-crossproject_2.12_1.0", "1.3.2")
    val creationDate: Instant = Instant.ofEpochSecond(1688667180L)
  }

  object Scala3 {
    val organization: Project.Organization = Project.Organization("scala")
    val reference: Project.Reference = Project.Reference.from("scala/scala3")
  }

  private def contributor(login: String): GithubContributor =
    GithubContributor(login, "", Url(""), 1)

}
