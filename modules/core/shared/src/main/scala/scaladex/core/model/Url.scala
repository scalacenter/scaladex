package scaladex.core.model

/**
 * Url type - self explained
 *
 * @param target the url target ex: http://scala-lang.org
 */
case class Url(target: String) extends AnyVal {
  def labeled(label: String): LabeledLink = LabeledLink(label, target)
}
