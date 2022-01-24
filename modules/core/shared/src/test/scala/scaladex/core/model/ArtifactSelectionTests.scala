package scaladex.core.model

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact._

class ArtifactSelectionTests extends AsyncFunSpec with Matchers {

  def emptyArtifact(
      maven: MavenReference,
      reference: Project.Reference
  ): Artifact = {
    val artifactId =
      ArtifactId.parse(maven.artifactId).getOrElse(throw new Exception(s"cannot parse ${maven.artifactId}"))
    val version = SemanticVersion.tryParse(maven.version).get
    Artifact(
      GroupId(maven.groupId),
      artifactId.value,
      version,
      artifactId.name,
      artifactId.platform,
      reference,
      None,
      None,
      None,
      Set.empty,
      isNonStandardLib = false
    )
  }

  def prepare(
      projectRef: Project.Reference,
      groupdId: String,
      artifacts: List[(String, String)]
  ): Seq[Artifact] =
    artifacts
      .map {
        case (artifactId, rawVersion) =>
          emptyArtifact(
            MavenReference(groupdId, artifactId, rawVersion),
            projectRef
          )
      }

  it("latest version pre release scala") {

    val projectRef = Project.Reference.from("typelevel", "cats")
    val project = Project.default(projectRef)
    val groupdId = "org.typelevel"
    val artifacts = prepare(
      projectRef,
      groupdId,
      List(
        ("cats-core_2.11", "0.6.0"),
        ("cats-core_2.11", "0.6.0-M2"),
        ("cats-core_2.11", "0.6.0-M1"),
        ("cats-core_2.11", "0.5.0"),
        ("cats-core_2.11", "0.4.1"),
        ("cats-core_2.11", "0.4.0"),
        ("cats-core_2.10", "0.6.0"),
        ("cats-core_2.10", "0.6.0-M2"),
        ("cats-core_2.10", "0.6.0-M1"),
        ("cats-core_2.10", "0.5.0"),
        ("cats-core_2.10", "0.4.1"),
        ("cats-core_2.10", "0.4.0"),
        ("cats-core_sjs0.6_2.11", "0.6.0"),
        ("cats-core_sjs0.6_2.11", "0.6.0-M2"),
        ("cats-core_sjs0.6_2.11", "0.6.0-M1"),
        ("cats-core_sjs0.6_2.11", "0.5.0"),
        ("cats-core_sjs0.6_2.11", "0.4.1"),
        ("cats-core_sjs0.6_2.11", "0.4.0"),
        ("cats-core_sjs0.6_2.10", "0.6.0"),
        ("cats-core_sjs0.6_2.10", "0.6.0-M2"),
        ("cats-core_sjs0.6_2.10", "0.6.0-M1"),
        ("cats-core_sjs0.6_2.10", "0.5.0"),
        ("cats-core_sjs0.6_2.10", "0.4.1"),
        ("cats-core_sjs0.6_2.10", "0.4.0")
      )
    )

    val result = ArtifactSelection.empty.filterArtifacts(artifacts, project)

    result should contain theSameElementsAs artifacts
  }

  it("selected artifact") {
    val projectRef = Project.Reference.from("akka", "akka")
    val project = Project.default(projectRef)
    val groupdId = "com.typesafe.akka"
    val artifacts =
      prepare(
        projectRef,
        groupdId,
        List(
          ("akka-distributed-data-experimental_2.11", "2.4.8"),
          ("akka-actors_2.11", "2.4.8")
        )
      )

    val selection = ArtifactSelection(
      artifactNames = Some(Artifact.Name("akka-distributed-data-experimental")),
      target = None,
      version = None,
      selected = None
    )

    val result = selection.filterArtifacts(artifacts, project)
    val expected = prepare(
      projectRef,
      groupdId,
      List(
        ("akka-distributed-data-experimental_2.11", "2.4.8")
      )
    )
    result should contain theSameElementsAs expected
  }
}
