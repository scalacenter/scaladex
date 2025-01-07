package scaladex.core.model

final case class DocumentationPattern(label: String, pattern: String):
  def asGlobal: Option[LabeledLink] =
    if DocumentationPattern.placeholders.exists(pattern.contains) then None
    else Some(LabeledLink(label, pattern))

  /** Documentation link are often related to a release version for example:
    * https://playframework.com/documentation/2.6.x/Home we want to substitute input such as
    * https://playframework.com/documentation/[major].[minor].x/Home
    */
  def eval(artifact: Artifact): LabeledLink =
    def major: String = artifact.version match
      case v: Version.SemanticLike => v.major.toString
      case v: Version.Custom => v.value.toString
    def minor: String = artifact.version match
      case Version.SemanticLike(_, Some(v), _, _, _, _) => v.toString
      case _ => ""
    val link = pattern
      .replace("[groupId]", artifact.groupId.value)
      .replace("[artifactId]", artifact.artifactId.value)
      .replace("[version]", artifact.version.value)
      .replace("[major]", major)
      .replace("[minor]", minor)
      .replace("[name]", artifact.name.value)
    LabeledLink(label, link)
  end eval
end DocumentationPattern

object DocumentationPattern:
  private val placeholders = Seq("[groupId]", "[artifactId]", "[version]", "[major]", "[minor]", "[name]")

  def validated(label: String, pattern: String): Option[DocumentationPattern] =
    if label.isEmpty || pattern.isEmpty then None
    else Some(DocumentationPattern(label, pattern))
