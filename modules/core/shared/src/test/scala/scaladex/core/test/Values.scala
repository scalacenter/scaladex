package scaladex.core.test

import java.time.Instant
import java.time.temporal.ChronoUnit

import scaladex.core.model.*
import scaladex.core.model.Artifact.*
import scaladex.core.model.ArtifactDependency.Scope
import scaladex.core.model.Project.Settings
import scaladex.core.model.search.ProjectDocument

object Values:
  val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  val ok: GithubStatus = GithubStatus.Ok(now)
  val unknown: GithubStatus = GithubStatus.Unknown(now)

  val `2.6.1`: Version = Version(2, 6, 1)
  val `4`: Version = Version(4)
  val `2.5.0`: Version = Version(2, 5, 0)
  val `7.0.0`: Version = Version(7, 0, 0)
  val `7.1.0`: Version = Version(7, 1, 0)
  val `7.2.0-PREVIEW.1`: Version = Version("7.2.0-PREVIEW.1")
  val `7.2.0-PREVIEW.2`: Version = Version("7.2.0-PREVIEW.2")
  val `7.2.0`: Version = Version(7, 2, 0)
  val `7.3.0`: Version = Version(7, 3, 0)

  val `_2.13`: BinaryVersion = BinaryVersion(Jvm, Scala.`2.13`)
  val `_3`: BinaryVersion = BinaryVersion(Jvm, Scala.`3`)
  val `_sjs1_3`: BinaryVersion = BinaryVersion(ScalaJs.`1.x`, Scala.`3`)
  val `_sjs0.6_2.13`: BinaryVersion = BinaryVersion(ScalaJs.`0.6`, Scala.`2.13`)
  val `_native0.4_2.13`: BinaryVersion = BinaryVersion(ScalaNative.`0.4`, Scala.`2.13`)

  private def contributor(login: String): GithubContributor =
    GithubContributor(login, "", Url(""), 1)

  private def developer(name: String, url: String, id: String) =
    Contributor(Some(name), None, Some(url), None, None, List(), None, Map(), Some(id))

  object Scalafix:
    val reference: Project.Reference = Project.Reference.from("scalacenter", "scalafix")

    val creationDate: Instant = Instant.ofEpochMilli(1475505237265L)
    val artifact: Artifact = Artifact(
      groupId = GroupId("ch.epfl.scala"),
      artifactId = ArtifactId("scalafix-core_2.13"),
      version = Version("0.9.30"),
      projectRef = reference,
      description = None,
      releaseDate = creationDate,
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false,
      fullScalaVersion = None,
      scaladocUrl = None,
      versionScheme = None,
      developers = Nil
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

    val projectHeader: ProjectHeader = ProjectHeader(
      reference,
      Seq(artifact),
      settings.defaultArtifact,
      settings.preferStableVersion
    ).get
    val projectDocument: ProjectDocument =
      ProjectDocument(
        project.copy(creationDate = Some(now.minus(1, ChronoUnit.MINUTES))),
        Some(projectHeader),
        0,
        Seq.empty
      )
  end Scalafix

  object PlayJsonExtra:
    val reference: Project.Reference = Project.Reference.from("xuwei-k", "play-json-extra")
    val creationDate: Instant = Instant.ofEpochMilli(1411736618000L)
    val artifact: Artifact = Artifact(
      groupId = GroupId("com.github.xuwei-k"),
      artifactId = ArtifactId("play-json-extra_2.11"),
      version = Version("0.1.1-play2.3-M1"),
      projectRef = reference,
      description = None,
      releaseDate = creationDate,
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false,
      fullScalaVersion = None,
      developers = Nil,
      scaladocUrl = None,
      versionScheme = None
    )
    val dependency: ArtifactDependency =
      ArtifactDependency(
        source = Cats.`core_3:2.6.1`.reference,
        target = artifact.reference,
        Scope("compile")
      )
    val githubInfo: GithubInfo = GithubInfo.empty
    val settings: Project.Settings = Project.Settings.empty.copy(
      defaultArtifact = Some(artifact.name),
      category = Some(Category.Json)
    )
  end PlayJsonExtra

  object Cats:
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
    val `core_3:2.6.1`: Artifact = getArtifact(
      "cats-core",
      `_3`,
      `2.6.1`,
      description = Some("Cats core"),
      fullScalaVersion = Some(Version("3.0.0")),
      scaladocUrl = Some(Url("http://typelevel.org/cats/api/")),
      versionScheme = Some("semver-spec"),
      developers = developers("org.typelevel:cats-core_3:jar:2.6.1")
    )
    val `core_2.13:2.6.1`: Artifact = getArtifact("cats-core", `_2.13`, `2.6.1`, description = Some("Cats core"))
    val `core_3:4`: Artifact = getArtifact("cats-core", `_3`, `4`, description = Some("Cats core"))
    val `core_2.13:2.5.0`: Artifact = getArtifact(
      "cats-core",
      `_2.13`,
      `2.5.0`,
      description = Some("Cats core"),
      fullScalaVersion = Some(Version(2, 13, 5)),
      scaladocUrl = Some(Url("http://typelevel.org/cats/api/")),
      versionScheme = Some("semver-spec"),
      developers = developers("org.typelevel:cats-core_2.13:jar:2.5.0"),
      creationDelta = -1 // created earlier
    )
    val `core_sjs1_3:2.6.1`: Artifact = getArtifact(
      "cats-core",
      `_sjs1_3`,
      `2.6.1`,
      description = Some("Cats core"),
      scaladocUrl = Some(Url("http://typelevel.org/cats/api/")),
      versionScheme = Some("semver-spec"),
      developers = developers("org.typelevel:cats-core_sjs1_3:jar:2.6.1")
    )
    val `core_sjs06_2.13:2.6.1`: Artifact =
      getArtifact("cats-core", `_sjs0.6_2.13`, `2.6.1`, description = Some("Cats core"))
    val `core_native04_2.13:2.6.1`: Artifact =
      getArtifact("cats-core", `_native0.4_2.13`, `2.6.1`, description = Some("Cats core"))
    val `kernel_2.13:2.6.1`: Artifact = getArtifact("cats-kernel", `_2.13`, `2.6.1`)
    val `kernel_3:2.6.1`: Artifact = getArtifact("cats-kernel", `_3`, `2.6.1`)
    val `laws_3:2.6.1`: Artifact = getArtifact("cats-laws", `_3`, `2.6.1`)
    val coreArtifacts: Seq[Artifact] = Seq(
      `core_3:2.6.1`,
      `core_2.13:2.5.0`,
      `core_sjs1_3:2.6.1`,
      `core_sjs06_2.13:2.6.1`,
      `core_native04_2.13:2.6.1`
    )
    val allArtifacts: Seq[Artifact] = coreArtifacts ++ Seq(`kernel_3:2.6.1`, `laws_3:2.6.1`)
    val dependencies: Seq[ArtifactDependency] = Seq(
      ArtifactDependency(
        source = `core_3:2.6.1`.reference,
        target = `kernel_3:2.6.1`.reference,
        Scope("compile")
      ),
      ArtifactDependency(
        source = `core_3:2.6.1`.reference,
        target = `laws_3:2.6.1`.reference,
        Scope("compile")
      ),
      ArtifactDependency(
        source = `core_3:2.6.1`.reference,
        target = Artifact.Reference.from("com.gu", "ztmp-scala-automation_2.10", "1.9"),
        Scope("compile")
      )
    )
    val projectHeader: ProjectHeader = ProjectHeader(reference, allArtifacts, None, true).get
    val projectDocument: ProjectDocument =
      ProjectDocument(
        project.copy(creationDate = Some(now.minus(10, ChronoUnit.MINUTES))),
        Some(projectHeader),
        1,
        Seq.empty
      )

    def developers(id: String): Seq[Contributor] = Seq(
      developer("Cody Allen", "https://github.com/ceedubs/", id),
      developer("Ross Baker", "https://github.com/rossabaker/", id),
      developer("P. Oscar Boykin", "https://github.com/johnynek/", id),
      developer("Travis Brown", "https://github.com/travisbrown/", id),
      developer("Adelbert Chang", "https://github.com/adelbertc/", id),
      developer("Peter Neyens", "https://github.com/peterneyens/", id),
      developer("Rob Norris", "https://github.com/tpolecat/", id),
      developer("Erik Osheim", "https://github.com/non/", id),
      developer("LukaJCB", "https://github.com/LukaJCB/", id),
      developer("Michael Pilquist", "https://github.com/mpilquist/", id),
      developer("Miles Sabin", "https://github.com/milessabin/", id),
      developer("Daniel Spiewak", "https://github.com/djspiewak/", id),
      developer("Frank Thomas", "https://github.com/fthomas/", id),
      developer("Julien Truffaut", "https://github.com/julien-truffaut/", id),
      developer("Kailuo Wang", "https://github.com/kailuowang/", id)
    )

    private def getArtifact(
        name: String,
        binaryVersion: BinaryVersion,
        version: Version,
        description: Option[String] = None,
        fullScalaVersion: Option[Version] = None,
        developers: Seq[Contributor] = Nil,
        scaladocUrl: Option[Url] = None,
        versionScheme: Option[String] = None,
        creationDelta: Int = 0
    ): Artifact =
      Artifact(
        groupId = groupId,
        artifactId = ArtifactId(Name(name), binaryVersion),
        version = version,
        projectRef = reference,
        description = description,
        releaseDate = Instant.ofEpochMilli(1620911032000L + 1000L * creationDelta),
        resolver = None,
        licenses = Set(license),
        isNonStandardLib = false,
        fullScalaVersion = fullScalaVersion,
        developers = developers,
        scaladocUrl = scaladocUrl,
        versionScheme = versionScheme
      )
  end Cats

  object CatsEffect:
    val dependency: ArtifactDependency = ArtifactDependency(
      source = Artifact.Reference.from("typelevel", "cats-effect-kernel_3", "3.2.3"),
      target = Cats.`core_3:2.6.1`.reference,
      Scope("compile")
    )

    val testDependency: ArtifactDependency = ArtifactDependency(
      source = Artifact.Reference.from("typelevel", "cats-effect-kernel_3", "3.2.3"),
      target = Artifact.Reference.from("typelevel", "scalacheck_3", "1.15.4"),
      Scope("test")
    )
  end CatsEffect

  object SbtCrossProject:
    val reference: Project.Reference = Project.Reference.from("portable-scala", "sbt-crossproject")
    val artifactRef: Artifact.Reference =
      Artifact.Reference.from("org.portable-scala", "sbt-scalajs-crossproject_2.12_1.0", "1.3.2")
    val creationDate: Instant = Instant.ofEpochSecond(1688667180L)

  object Scala3:
    val organization: Project.Organization = Project.Organization("scala")
    val reference: Project.Reference = Project.Reference.unsafe("scala/scala3")
  
  object CompilerPluginProj:
    val reference: Project.Reference = Project.Reference.unsafe("org/example-compiler-plugin")
    val projectDocument: ProjectDocument =
      ProjectDocument.default(reference).copy(
        languages = Seq(Scala.`2.13`),
        platforms = Seq(CompilerPlugin)
      )
end Values
