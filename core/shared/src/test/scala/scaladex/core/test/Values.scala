package scaladex.core.test

import java.time.Instant
import java.time.temporal.ChronoUnit

import scaladex.core.model.Artifact
import scaladex.core.model.Artifact._
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubInfo
import scaladex.core.model.GithubIssue
import scaladex.core.model.GithubStatus
import scaladex.core.model.Platform
import scaladex.core.model.Platform._
import scaladex.core.model.Project
import scaladex.core.model.Project.Settings
import scaladex.core.model.SemanticVersion
import scaladex.core.model.Url
import scaladex.core.model.search.ProjectDocument

object Values {
  val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  val ok: GithubStatus = GithubStatus.Ok(now)

  val `2.6.1` = SemanticVersion(2, 6, 1)
  val `2.7.0` = SemanticVersion(2, 7, 0)
  val `7.0.0` = SemanticVersion(7, 0, 0)
  val `7.1.0` = SemanticVersion(7, 1, 0)
  val `7.2.0` = SemanticVersion(7, 2, 0)
  val `7.3.0` = SemanticVersion(7, 3, 0)

  object Scalafix {
    val reference: Project.Reference = Project.Reference.from("scalacenter", "scalafix")
    val creationDate: Instant = Instant.ofEpochMilli(1475505237265L)
    val artifactId: ArtifactId = ArtifactId.parse("scalafix-core_2.13").get
    val artifact: Artifact = Artifact(
      groupId = GroupId("ch.epfl.scala"),
      artifactId = artifactId.value,
      version = SemanticVersion.tryParse("0.9.30").get,
      artifactName = artifactId.name,
      platform = artifactId.platform,
      projectRef = reference,
      description = None,
      releaseDate = Some(creationDate),
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false
    )
    val githubInfo: GithubInfo =
      GithubInfo.empty
        .copy(
          stars = Some(643),
          forks = Some(148),
          topics = Set("refactoring", "dotty", "linter", "metaprogramming", "scalafix", "sbt", "rewrite", "scala")
        )
    val settings: Settings = Settings(
      defaultStableVersion = false,
      defaultArtifact = None,
      strictVersions = false,
      customScalaDoc = None,
      documentationLinks = List(),
      deprecated = false,
      contributorsWanted = false,
      artifactDeprecations = Set(),
      cliArtifacts = Set(),
      primaryTopic = Some("Scala3"),
      beginnerIssuesLabel = None
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
      version = SemanticVersion.tryParse("0.1.1-play2.3-M1").get,
      artifactName = artifactId.name,
      platform = artifactId.platform,
      projectRef = reference,
      description = None,
      releaseDate = Some(creationDate),
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false
    )
    val dependency: ArtifactDependency =
      ArtifactDependency(
        source = Cats.`core_3:2.6.1`.mavenReference,
        target = artifact.mavenReference,
        "compile"
      )
    val githubInfo: GithubInfo = GithubInfo.empty
    val settings: Project.Settings =
      Project.Settings.default.copy(
        defaultArtifact = Some(artifact.artifactName),
        primaryTopic = Some("serialization")
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
        chatroom = Some(Url("https://gitter.im/typelevel/cats")),
        openIssues = List(issueAboutFoo, issueAboutBar)
      )
    val project: Project = Project.default(
      reference,
      githubInfo = Some(githubInfo),
      now = now
    )
    val groupId: GroupId = GroupId("org.typelevel")

    private def getArtifact(
        name: String,
        platform: Platform,
        version: SemanticVersion = `2.6.1`
    ): Artifact = {
      val artifactId = ArtifactId(Name(name), platform)
      Artifact(
        groupId = groupId,
        artifactId = artifactId.value,
        version = version,
        artifactName = artifactId.name,
        platform = platform,
        projectRef = reference,
        description = None,
        releaseDate = Some(Instant.ofEpochMilli(1620911032000L)),
        resolver = None,
        licenses = Set(),
        isNonStandardLib = false
      )
    }

    val `core_3:2.6.1`: Artifact = getArtifact("cats-core", ScalaJvm.`3`)
    val `core_3:2.7.0`: Artifact = getArtifact("cats-core", ScalaJvm.`3`, `2.7.0`)
    val core_sjs1_3: Artifact = getArtifact("cats-core", ScalaJs.`1_3`)
    val core_sjs06_213: Artifact = getArtifact("cats-core", ScalaJs.`0.6_2.13`)
    val core_native04_213: Artifact = getArtifact("cats-core", ScalaNative.`0.4_2.13`)
    val kernel_3: Artifact = getArtifact("cats-kernel", ScalaJvm.`3`)
    val laws_3: Artifact = getArtifact("cats-laws", ScalaJvm.`3`)

    val allArtifacts: Seq[Artifact] =
      Seq(`core_3:2.6.1`, `core_3:2.7.0`, core_sjs1_3, core_sjs06_213, core_native04_213, kernel_3, laws_3)

    val dependencies: Seq[ArtifactDependency] = Seq(
      ArtifactDependency(
        source = `core_3:2.6.1`.mavenReference,
        target = kernel_3.mavenReference,
        "compile"
      ),
      ArtifactDependency(source = `core_3:2.6.1`.mavenReference, target = laws_3.mavenReference, "compile"),
      ArtifactDependency(
        source = `core_3:2.6.1`.mavenReference,
        target = MavenReference(
          "com.gu",
          "ztmp-scala-automation_2.10",
          "1.9"
        ), // dependency with a corresponding getArtifact
        "compile"
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
      "compile"
    )

    val testDependency: ArtifactDependency = ArtifactDependency(
      source = MavenReference(
        "cats-effect",
        "cats-effect-kernel_3",
        "3.2.3"
      ),
      target = MavenReference("typelevel", "scalacheck_3", "1.15.4"),
      "test"
    )
  }

}
