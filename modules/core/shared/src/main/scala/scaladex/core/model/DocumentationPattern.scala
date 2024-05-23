package scaladex.core.model

final case class DocumentationPattern(label: String, pattern: String) {
  def asGlobal: Option[LabeledLink] =
    if (DocumentationPattern.placeholders.exists(pattern.contains)) None
    else Some(LabeledLink(label, pattern))

  /**
   * Documentation link are often related to a release version
   * for example: https://playframework.com/documentation/2.6.x/Home
   * we want to substitute input such as
   * https://playframework.com/documentation/[major].[minor].x/Home
   */
  def eval(artifact: Artifact): LabeledLink = {
    val link = pattern
      .replace("[groupId]", artifact.groupId.value)
      .replace("[artifactId]", artifact.artifactId)
      .replace("[version]", artifact.version.toString)
      .replace("[major]", artifact.version.major.toString)
      .replace("[minor]", artifact.version.minor.toString)
      .replace("[name]", artifact.artifactName.value)
    LabeledLink(label, link)
  }
}

object DocumentationPattern {
  private val placeholders = Seq("[groupId]", "[artifactId]", "[version]", "[major]", "[minor]", "[name]")

  def validated(label: String, pattern: String): Option[DocumentationPattern] =
    if (label.isEmpty || pattern.isEmpty) None
    else Some(DocumentationPattern(label, pattern))
}
