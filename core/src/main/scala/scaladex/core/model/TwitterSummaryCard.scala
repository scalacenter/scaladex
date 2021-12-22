package scaladex.core.model

/**
 * A twitter summary card, see https://dev.twitter.com/cards/types/summary.
 *
 * @param site        The Twitter handle (including @) of the site
 * @param title       The title of the card
 * @param description A description.  Truncated, not all clients show it
 * @param image       URL to an image representing the content
 * @param imageAlt    Alt text for the image
 */
case class TwitterSummaryCard(
    site: String,
    title: String,
    description: String,
    image: Option[Url] = None,
    imageAlt: Option[String] = None
) {
  val card: String = "summary"

  /**
   * Convert the twitter card to a corresponding list of meta tags.
   */
  def toHeadMeta: Seq[HeadMeta] =
    Seq(
      HeadMeta(name = "twitter:card", content = card),
      HeadMeta(name = "twitter:site", content = site),
      HeadMeta(name = "twitter:title", content = title),
      HeadMeta(name = "twitter:description", content = description)
    ) ++
      image.map(c => HeadMeta(name = "twitter:image", content = c.target)) ++
      imageAlt.map(c => HeadMeta(name = "twitter:image:alt", content = c))
}
