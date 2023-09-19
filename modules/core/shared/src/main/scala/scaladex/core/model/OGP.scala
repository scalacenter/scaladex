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
  def toHeadMetaProperty: Seq[HeadMetaProperty] = Seq(
    HeadMetaProperty(property = "og:title", content = title),
    HeadMetaProperty(property = "og:url", content = url.target),
    HeadMetaProperty(property = "og:type", content = `type`),
    HeadMetaProperty(property = "og:description", content = description),
    HeadMetaProperty(property = "og:site_name", content = siteName)
  ) ++ image.map(c => HeadMetaProperty(property = "og:image", content = c.target)) ++ imageAlt.map(c =>
    HeadMetaProperty(property = "og:image:alt", c)
  )
}
