package scaladex.core.model

/**
  * Open Graph Protocol, see https://ogp.me/
  *
  * @param title
  * @param url
  * @param description
  * @param image
  * @param imageAlt
  */
case class OGP(
    title: String,
    url: Url,
    description: String,
    image: Option[Url] = None,
    imageAlt: Option[String] = None
) {
  val `type`: String = "article"
  val siteName: String = "Scaladex"
  def toHeadMeta: Seq[HeadMeta] = Seq(
    HeadMeta(name = "og:title", content = title),
    HeadMeta(name = "og:url", content = url.toString()),
    HeadMeta(name = "og:description", content = description),
    HeadMeta(name = "og:site_name", content = siteName)
  ) ++ image.map(c => HeadMeta(name = "og:image", content = c.target)) ++ imageAlt.map(c =>
    HeadMeta(name = "og:image:alt", c)
  )
}
