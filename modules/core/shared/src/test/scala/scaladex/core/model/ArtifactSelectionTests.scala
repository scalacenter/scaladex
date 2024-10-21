package scaladex.core.model

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class ArtifactSelectionTests extends AsyncFunSpec with Matchers {

  def artifactRef(groupdId: String, artifactId: String, version: String): Artifact.Reference =
    Artifact.Reference(
      Artifact.GroupId(groupdId),
      Artifact.ArtifactId(artifactId),
      SemanticVersion.parse(version).get
    )

  it("latest version pre release scala") {
    val project = Project.default(Project.Reference.from("typelevel", "cats"))
    val artifactIds = Seq("cats-core_2.11", "cats-core_2.10", "cats-core_sjs0.6_2.11", "cats-core_sjs0.6_2.10")
    val versions = Seq("0.6.0", "0.6.0-M2", "0.6.0-M1", "0.5.0", "0.4.1", "0.4.0")
    val artifacts = for {
      artifactId <- artifactIds
      version <- versions
    } yield artifactRef("org.typelevel", artifactId, version)
    val result = ArtifactSelection.empty.defaultArtifact(artifacts, project)
    result should contain(artifacts.head)
  }

  it("selected artifact") {
    val project = Project.default(Project.Reference.from("akka", "akka"))
    val groupdId = "com.typesafe.akka"
    val artifacts = Seq(
      artifactRef(groupdId, "akka-distributed-data-experimental_2.11", "2.4.8"),
      artifactRef(groupdId, "akka-actors_2.11", "2.4.8")
    )
    val selection = ArtifactSelection(None, Some(Artifact.Name("akka-distributed-data-experimental")))
    val result = selection.defaultArtifact(artifacts, project)
    result should contain(artifacts.head)
  }
}
