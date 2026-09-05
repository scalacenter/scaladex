package scaladex.core.api

import scaladex.core.model.Artifact
import scaladex.core.model.Category
import scaladex.core.model.DocumentationPattern
import scaladex.core.model.Project

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ProjectSettingsPatchTests extends AnyFunSpec with Matchers:
  val noChanges: ProjectSettingsPatch = ProjectSettingsPatch(None, None, None, None, None, None, None, None, None)

  val current: Project.Settings = Project.Settings.empty.copy(
    preferStableVersion = false,
    defaultArtifact = Some(Artifact.Name("cats-core")),
    customScalaDoc = Some("https://example.org/api"),
    documentationLinks = List(DocumentationPattern("Guide", "https://example.org/guide")),
    contributorsWanted = true,
    deprecatedArtifacts = Set(Artifact.Name("cats-old")),
    cliArtifacts = Set(Artifact.Name("cats-cli")),
    category = Some(Category.Json),
    chatroom = Some("#cats")
  )

  describe("applyTo") {
    it("leaves every field unchanged when the patch is empty") {
      noChanges.applyTo(current) shouldBe current
    }

    it("updates only the fields present in the patch") {
      val patch = noChanges.copy(contributorsWanted = Some(false), preferStableVersion = Some(true))
      patch.applyTo(current) shouldBe current.copy(contributorsWanted = false, preferStableVersion = true)
    }

    it("clears a nullable field when given an empty string") {
      val patch = noChanges.copy(customScalaDoc = Some(""), chatroom = Some(""), defaultArtifact = Some(""))
      val updated = patch.applyTo(current)
      updated.customScalaDoc shouldBe None
      updated.chatroom shouldBe None
      updated.defaultArtifact shouldBe None
    }

    it("sets a nullable field when given a non-empty value") {
      val patch = noChanges.copy(chatroom = Some("#new"), defaultArtifact = Some("cats-effect"))
      val updated = patch.applyTo(current)
      updated.chatroom shouldBe Some("#new")
      updated.defaultArtifact shouldBe Some(Artifact.Name("cats-effect"))
    }

    it("replaces list fields wholesale") {
      val patch = noChanges.copy(
        deprecatedArtifacts = Some(Seq(Artifact.Name("a"), Artifact.Name("b"))),
        cliArtifacts = Some(Seq.empty),
        documentationLinks = Some(Seq.empty)
      )
      val updated = patch.applyTo(current)
      updated.deprecatedArtifacts shouldBe Set(Artifact.Name("a"), Artifact.Name("b"))
      updated.cliArtifacts shouldBe empty
      updated.documentationLinks shouldBe empty
    }

    it("resolves a known category label and clears it on empty string") {
      noChanges.copy(category = Some("web-frontend")).applyTo(current).category shouldBe Some(Category.WebFrontend)
      noChanges.copy(category = Some("")).applyTo(current).category shouldBe None
    }

    it("keeps the current category when handed an unknown label") {
      noChanges.copy(category = Some("not-a-category")).applyTo(current).category shouldBe current.category
    }
  }

  describe("invalidCategory") {
    it("flags an unknown non-empty category label") {
      ProjectSettingsPatch.invalidCategory(Some("not-a-category")) shouldBe Some("not-a-category")
    }

    it("accepts a known label, an empty string and an absent value") {
      ProjectSettingsPatch.invalidCategory(Some("json")) shouldBe None
      ProjectSettingsPatch.invalidCategory(Some("")) shouldBe None
      ProjectSettingsPatch.invalidCategory(None) shouldBe None
    }
  }
end ProjectSettingsPatchTests
