package ch.epfl.scala.index

import java.time.Instant

import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.misc.Url
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Platform._
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.DataForm
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.index.newModel.ReleaseDependency
import ch.epfl.scala.search.ProjectDocument

object Values {

  object Scalafix {
    val project: NewProject =
      NewProject.defaultProject(
        "scalacenter",
        "scalafix",
        created = Some(Instant.ofEpochMilli(1475505237265L))
      )

    val reference: NewProject.Reference = project.reference
    val githubInfo: GithubInfo =
      GithubInfo
        .empty(reference.repository.value, reference.organization.value)
        .copy(
          stars = Some(643),
          forks = Some(148),
          contributorCount = 76
        )
    val projectWithGithubInfo: NewProject =
      project.copy(githubInfo = Some(githubInfo))

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
      primaryTopic = Some("Scala3")
    )
    val projectWithdataForm: NewProject =
      project.copy(dataForm = dataForm)

    val release: NewRelease = NewRelease(
      MavenReference(
        "ch.epfl.scala",
        "scalafix-core_2.13",
        "0.9.30"
      ),
      version = SemanticVersion.tryParse("0.9.30").get,
      organization = reference.organization,
      repository = reference.repository,
      artifactName = ArtifactName("scalafix-core"),
      platform = ScalaJvm(ScalaVersion.`2.13`),
      description = None,
      releasedAt = None,
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false
    )

    val projectDocument: ProjectDocument = ProjectDocument(projectWithGithubInfo, Seq(release), 0)
  }

  object PlayJsonExtra {
    val project: NewProject =
      NewProject.defaultProject("xuwei-k", "play-json-extra")
    val reference: NewProject.Reference = project.reference
    val release: NewRelease = NewRelease(
      MavenReference(
        "com.github.xuwei-k",
        "play-json-extra_2.10",
        "0.1.1-play2.3-M1"
      ),
      version = SemanticVersion.tryParse("0.1.1-play2.3-M1").get,
      organization = reference.organization,
      repository = reference.repository,
      artifactName = ArtifactName("play-json-extra"),
      platform = ScalaJvm(ScalaVersion.`2.11`),
      description = None,
      releasedAt = None,
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false
    )
    val dependency: ReleaseDependency =
      ReleaseDependency(
        source = Cats.core_3.maven,
        target = release.maven,
        "compile"
      )
  }

  object Cats {
    val project: NewProject = NewProject.defaultProject(
      "typelevel",
      "cats",
      created = Some(Instant.ofEpochMilli(1454649333334L))
    )
    val reference: NewProject.Reference = project.reference
    val issueAboutFoo: GithubIssue = GithubIssue(1, "Issue about foo", Url("https://github.com/typelevel/cats/pull/1"))
    val issueAboutBar: GithubIssue = GithubIssue(2, "Issue about bar", Url("https://github.com/typelevel/cats/pull/2"))
    val githubInfo: GithubInfo = GithubInfo
      .empty(reference.repository.value, reference.organization.value)
      .copy(
        stars = Some(4337),
        forks = Some(1081),
        contributorCount = 392,
        contributingGuide = Some(Url("https://github.com/typelevel/cats/blob/main/CONTRIBUTING.md")),
        chatroom = Some(Url("https://gitter.im/typelevel/cats")),
        beginnerIssues = List(issueAboutFoo, issueAboutBar)
      )
    val projectWithGithubInfo: NewProject = project.copy(githubInfo = Some(githubInfo))
    private def release(
        name: String,
        platform: Platform,
        version: String = "2.6.1"
    ): NewRelease =
      NewRelease(
        MavenReference(
          "org.typelevel",
          s"${name}_$platform",
          version
        ),
        SemanticVersion.tryParse(version).get,
        organization = reference.organization,
        repository = reference.repository,
        artifactName = ArtifactName(name),
        platform = platform,
        description = None,
        releasedAt = None,
        resolver = None,
        licenses = Set(),
        isNonStandardLib = false
      )

    val core_3: NewRelease = release("cats-core", ScalaJvm.`3`)
    val core_sjs1_3: NewRelease = release("cats-core", ScalaJs.`1_3`)
    val core_sjs06_213: NewRelease = release("cats-core", ScalaJs.`0.6_2.13`)
    val core_native04_213: NewRelease = release("cats-core", ScalaNative.`0.4_2.13`)
    val kernel_3: NewRelease = release("cats-kernel", ScalaJvm.`3`)
    val laws_3: NewRelease = release("cats-laws", ScalaJvm.`3`)

    val allReleases: Seq[NewRelease] = Seq(core_3, core_sjs1_3, core_sjs06_213, core_native04_213, kernel_3, laws_3)

    val dependencies: Seq[ReleaseDependency] = Seq(
      ReleaseDependency(
        source = core_3.maven,
        target = kernel_3.maven,
        "compile"
      ),
      ReleaseDependency(source = core_3.maven, target = laws_3.maven, "compile"),
      ReleaseDependency(
        source = core_3.maven,
        target = MavenReference(
          "com.gu",
          "ztmp-scala-automation_2.10",
          "1.9"
        ), // dependency with a corresponding release
        "compile"
      )
    )

    val projectDocument: ProjectDocument = ProjectDocument(projectWithGithubInfo, allReleases, 1)
  }

  object CatsEffect {
    val dependency: ReleaseDependency = ReleaseDependency(
      source = MavenReference(
        "cats-effect",
        "cats-effect-kernel_3",
        "3.2.3"
      ),
      target = Cats.core_3.maven,
      "compile"
    )

    val testDependency: ReleaseDependency = ReleaseDependency(
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
