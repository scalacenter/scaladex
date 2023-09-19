package scaladex.core.model

/**
 * A meta tag in head that uses property attribute.
 *
 * @param property The property attribute
 * @param content  The content attribute
 */
case class HeadMetaProperty(property: String, content: String)
