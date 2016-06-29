package ch.epfl.scala.index
package data
package project

import model._
import model.misc.MavenReference
import model.release.{ScalaTarget, SemanticVersion}

import utest._

object DeltaTest extends TestSuite{
  private val live = List(
    Project(
      Project.Reference("typelevel", "cats"),
      List(
        Artifact(
          Artifact.Reference("typelevel", "cats-core"),
          List(
            Release(
              MavenReference("org.typelevel", "cats-core_2.11", "0.6.0"),
              Release.Reference(
                "typelevel",
                "cats-core",
                SemanticVersion(0, 6, Some(0)),
                ScalaTarget(SemanticVersion(2, 11))
              )
            )
          )
        ),
        Artifact(
          Artifact.Reference("typelevel", "cats-free"),
          List(
            Release(
              MavenReference("org.typelevel", "cats-free_2.11", "0.6.0"),
              Release.Reference(
                "typelevel",
                "cats-free",
                SemanticVersion(0, 6, Some(0)),
                ScalaTarget(SemanticVersion(2, 11))
              )
            )
          )
        )
      )
    )
  )

  val tests = this{
    "empty"-{
      assert(ProjectDelta(List(), List()) == List())
    }
    "empty new"-{
      assert(ProjectDelta(live, List()) == List(NoOp))
    }
    "new project, new artifact, new release"-{
      val newProject = Project(Project.Reference("foo", "bar"), Nil)
      val newArtifact =
        Artifact(
          Artifact.Reference("typelevel", "cats-dogs"),
          List(
            Release(
              MavenReference("org.typelevel", "cats-dogs_2.11", "0.6.0"),
              Release.Reference(
                "typelevel",
                "cats-dogs",
                SemanticVersion(0, 6, Some(0)),
                ScalaTarget(SemanticVersion(2, 11))
              )
            )
          )
        )

      val newRelease =
        Release(
          MavenReference("org.typelevel", "cats-free_2.11", "0.7.0"),
          Release.Reference(
            "typelevel",
            "cats-free",
            SemanticVersion(0, 7, Some(0)),
            ScalaTarget(SemanticVersion(2, 11))
          )
        )

      val projectReference = Project.Reference("typelevel", "cats")
      val artifactReference = Artifact.Reference("typelevel", "cats-free")

      val update = List(
        newProject,
        Project(
          projectReference,
          List(
            newArtifact,
            Artifact(
              artifactReference,
              List(newRelease)
            )
          )
        )
      )

      val deltas = ProjectDelta(live, update)

      assert(deltas.size == 2)

      val newProjectDelta = deltas.collect{ case NewProject(np) => np }.head
      assert(newProjectDelta == newProject)

      val updatedProjectDelta = deltas.collect{ case UpdatedProject(up) => up }.head
      assert(updatedProjectDelta.artifacts.contains(newArtifact))
      assert(updatedProjectDelta.artifacts.exists(_.releases.contains(newRelease)))
    }
  }
}