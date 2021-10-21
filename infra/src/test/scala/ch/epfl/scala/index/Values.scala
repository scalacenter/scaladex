package ch.epfl.scala.index

import ch.epfl.scala.index.model.Project
import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.Platform._
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.DataForm
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.index.newModel.ReleaseDependency

object Values {

  object Scalafix {
    val project: NewProject =
      NewProject.defaultProject(
        "scalacenter",
        "scalafix",
        created = None
      )

    val reference: NewProject.Reference = project.reference
    val githubInfo = GithubInfo.empty
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
      released = None,
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false
    )
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
      released = None,
      resolver = None,
      licenses = Set(),
      isNonStandardLib = false
    )
    val dependency: ReleaseDependency = {
      ReleaseDependency(
        source = Cats.core.maven,
        target = release.maven,
        "compile"
      )
    }
  }

  object Cats {
    val project: NewProject = NewProject.defaultProject(
      "typelevel",
      "cats",
      created = None
    )
    val reference: NewProject.Reference = project.reference
    private def release(
        artifactName: ArtifactName,
        artifactId: String
    ): NewRelease =
      NewRelease(
        MavenReference(
          "org.typelevel",
          artifactId,
          "2.6.1"
        ),
        SemanticVersion.tryParse("2.6.1").get,
        organization = reference.organization,
        repository = reference.repository,
        artifactName = artifactName,
        platform = ScalaJvm(Scala3Version.`3`),
        description = None,
        released = None,
        resolver = None,
        licenses = Set(),
        isNonStandardLib = false
      )

    val core: NewRelease = release(ArtifactName("cats-core"), "cats-core_3")
    val kernel: NewRelease =
      release(ArtifactName("cats-kernel"), "cats-kernel_3")
    val laws: NewRelease =
      release(ArtifactName("cats-laws"), "cats-laws_3")

    val dependencies: Seq[ReleaseDependency] = Seq(
      ReleaseDependency(
        source = core.maven,
        target = kernel.maven,
        "compile"
      ),
      ReleaseDependency(source = core.maven, target = laws.maven, "compile"),
      ReleaseDependency(
        source = core.maven,
        target = MavenReference(
          "com.gu",
          "ztmp-scala-automation_2.10",
          "1.9"
        ), // dependency with a corresponding release
        "compile"
      )
    )

    val dependency: ReleaseDependency = ReleaseDependency(
      source = MavenReference(
        "cats-effect",
        "cats-effect-kernel_3",
        "3.2.3"
      ),
      target = MavenReference("org.typelevel", "cats-core_3", "2.6.1"),
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

    val projectDocument: Project = Project(
      reference.organization.value,
      reference.repository.value,
      defaultArtifact = Some(core.artifactName.value),
      artifacts = List(core.artifactName.value, kernel.artifactName.value),
      releaseCount = 2,
      created = None,
      updated = None,
      targetType = List("Jvm"),
      scalaVersion = List("scala3"),
      scalaJsVersion = List.empty,
      scalaNativeVersion = List.empty,
      sbtVersion = List.empty,
      dependencies = Set.empty,
      dependentCount = 0
    )
  }

}
