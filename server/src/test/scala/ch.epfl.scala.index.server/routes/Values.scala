package ch.epfl.scala.index.server.routes

import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.misc.GithubInfo
import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.ScalaJvm
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewProject.Organization
import ch.epfl.scala.index.newModel.NewProject.Repository
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName

object Values {
  // Mock Data for tests
  val release: NewRelease = NewRelease(
    MavenReference(
      "com.github.xuwei-k",
      "play-json-extra_2.10",
      "0.1.1-play2.3-M1"
    ),
    version = SemanticVersion.tryParse("0.1.1-play2.3-M1").get,
    organization = Organization("xuwei-k"),
    repository = Repository("play-json-extra"),
    artifact = ArtifactName("play-json-extra"),
    target = Some(ScalaJvm(ScalaVersion.`2.11`)),
    description = None,
    released = None,
    resolver = None,
    licenses = Set(),
    isNonStandardLib = false
  )
  val project: NewProject = NewProject(
    release.organization,
    release.repository,
    Some(GithubInfo.empty),
    None,
    NewProject.FormData.default
  )
}
