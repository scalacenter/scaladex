package scaladex.core.api

import scaladex.core.model.Artifact
import scaladex.core.model.Category
import scaladex.core.model.DocumentationPattern
import scaladex.core.model.Project

/** A partial update of [[Project.Settings]].
  *
  * Every field is optional: a field that is absent from the request is left unchanged. For the nullable fields
  * (`defaultArtifact`, `customScalaDoc`, `category`, `chatroom`) an empty string clears the current value, mirroring
  * the behaviour of the web settings form. A non-empty `category` must be a known category label (validated when the
  * request is decoded).
  */
case class ProjectSettingsPatch(
    preferStableVersion: Option[Boolean],
    defaultArtifact: Option[String],
    customScalaDoc: Option[String],
    documentationLinks: Option[Seq[DocumentationPattern]],
    contributorsWanted: Option[Boolean],
    deprecatedArtifacts: Option[Seq[Artifact.Name]],
    cliArtifacts: Option[Seq[Artifact.Name]],
    category: Option[String],
    chatroom: Option[String]
):
  def applyTo(settings: Project.Settings): Project.Settings =

    settings.copy(
      preferStableVersion = preferStableVersion.getOrElse(settings.preferStableVersion),
      defaultArtifact = defaultArtifact match
        case Some("") => None
        case Some(name) => Some(Artifact.Name.apply(name))
        case None => settings.defaultArtifact,
      customScalaDoc = customScalaDoc match
        case Some("") => None
        case some: Some[String] => some
        case None => settings.customScalaDoc,
      documentationLinks = documentationLinks.getOrElse(settings.documentationLinks),
      contributorsWanted = contributorsWanted.getOrElse(settings.contributorsWanted),
      deprecatedArtifacts = deprecatedArtifacts.map(_.toSet).getOrElse(settings.deprecatedArtifacts),
      cliArtifacts = cliArtifacts.map(_.toSet).getOrElse(settings.cliArtifacts),
      category = category match
        case Some("") => None
        case Some(name) => Category.byLabel.get(name).orElse(settings.category)
        case None => settings.category,
      chatroom = chatroom match
        case Some("") => None
        case some: Some[String] => some
        case None => settings.chatroom
    )
  end applyTo
end ProjectSettingsPatch

object ProjectSettingsPatch:
  /** Returns the invalid category label, if `category` is present, non-empty and unknown. */
  def invalidCategory(category: Option[String]): Option[String] =
    category.filter(label => label.nonEmpty && !Category.byLabel.contains(label))
