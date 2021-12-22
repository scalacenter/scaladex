package ch.epfl.scala.index

import java.time.Instant
import java.time.temporal.ChronoUnit

import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.Url
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Platform._
import ch.epfl.scala.index.newModel.Artifact
import ch.epfl.scala.index.newModel.Artifact.ArtifactId
import ch.epfl.scala.index.newModel.Artifact.GroupId
import ch.epfl.scala.index.newModel.Artifact.MavenReference
import ch.epfl.scala.index.newModel.Artifact.Name
import ch.epfl.scala.index.newModel.ArtifactDependency
import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.index.newModel.Project.DataForm
import ch.epfl.scala.search.ProjectDocument

object Values {
  val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
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
      GithubInfo
        .empty(reference.repository.value, reference.organization.value)
        .copy(
          stars = Some(643),
          forks = Some(148),
          topics = Set("refactoring", "dotty", "linter", "metaprogramming", "scalafix", "sbt", "rewrite", "scala")
        )
    val dataForm: DataForm = DataForm(
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
      Project.default(reference, None, Some(githubInfo), Some(dataForm), now = now)

    val projectDocument: ProjectDocument =
      ProjectDocument(project.copy(creationDate = Some(now.minus(1, ChronoUnit.MINUTES))), Seq(artifact), 0, Seq.empty)
  }

  object PlayJsonExtra {
    val reference: Project.Reference = Project.Reference.from("xuwei-k", "play-json-extra")
    val artifactId: ArtifactId = ArtifactId.parse("play-json-extra_2.11").get
    val artifact: Artifact = Artifact(
      groupId = GroupId("com.github.xuwei-k"),
      artifactId = artifactId.value,
      version = SemanticVersion.tryParse("0.1.1-play2.3-M1").get,
      artifactName = artifactId.name,
      platform = artifactId.platform,
      projectRef = reference,
      description = None,
      releaseDate = Some(Instant.ofEpochMilli(1411736618000L)),
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false
    )
    val dependency: ArtifactDependency =
      ArtifactDependency(
        source = Cats.core_3.mavenReference,
        target = artifact.mavenReference,
        "compile"
      )
  }

  object Cats {
    val reference: Project.Reference = Project.Reference.from("typelevel", "cats")
    val issueAboutFoo: GithubIssue = GithubIssue(1, "Issue about foo", Url("https://github.com/typelevel/cats/pull/1"))
    val issueAboutBar: GithubIssue = GithubIssue(2, "Issue about bar", Url("https://github.com/typelevel/cats/pull/2"))
    val githubInfo: GithubInfo = GithubInfo
      .empty(reference.repository.value, reference.organization.value)
      .copy(
        stars = Some(4337),
        forks = Some(1081),
        contributingGuide = Some(Url("https://github.com/typelevel/cats/blob/main/CONTRIBUTING.md")),
        chatroom = Some(Url("https://gitter.im/typelevel/cats")),
        beginnerIssues = List(issueAboutFoo, issueAboutBar)
      )
    val project: Project = Project.default(
      reference,
      githubInfo = Some(githubInfo),
      now = now
    )
    private def getArtifact(
        name: String,
        platform: Platform,
        version: String = "2.6.1"
    ): Artifact = {
      val artifactId = ArtifactId(Name(name), platform)
      Artifact(
        groupId = GroupId("org.typelevel"),
        artifactId = artifactId.value,
        version = SemanticVersion.tryParse(version).get,
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

    val core_3: Artifact = getArtifact("cats-core", ScalaJvm.`3`)
    val core_sjs1_3: Artifact = getArtifact("cats-core", ScalaJs.`1_3`)
    val core_sjs06_213: Artifact = getArtifact("cats-core", ScalaJs.`0.6_2.13`)
    val core_native04_213: Artifact = getArtifact("cats-core", ScalaNative.`0.4_2.13`)
    val kernel_3: Artifact = getArtifact("cats-kernel", ScalaJvm.`3`)
    val laws_3: Artifact = getArtifact("cats-laws", ScalaJvm.`3`)

    val allReleases: Seq[Artifact] = Seq(core_3, core_sjs1_3, core_sjs06_213, core_native04_213, kernel_3, laws_3)

    val dependencies: Seq[ArtifactDependency] = Seq(
      ArtifactDependency(
        source = core_3.mavenReference,
        target = kernel_3.mavenReference,
        "compile"
      ),
      ArtifactDependency(source = core_3.mavenReference, target = laws_3.mavenReference, "compile"),
      ArtifactDependency(
        source = core_3.mavenReference,
        target = MavenReference(
          "com.gu",
          "ztmp-scala-automation_2.10",
          "1.9"
        ), // dependency with a corresponding getArtifact
        "compile"
      )
    )

    val projectDocument: ProjectDocument =
      ProjectDocument(project.copy(creationDate = Some(now.minus(1, ChronoUnit.MINUTES))), allReleases, 1, Seq.empty)
  }

  object CatsEffect {
    val dependency: ArtifactDependency = ArtifactDependency(
      source = MavenReference(
        "cats-effect",
        "cats-effect-kernel_3",
        "3.2.3"
      ),
      target = Cats.core_3.mavenReference,
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
