package ch.epfl.scala.index.model.misc

/**
  * Avatar url trait to provide some functions on that
  */
trait AvatarUrl {

  val avatarUrl: String

  /**
    * returns an avatar url which returns the avatar in specific size.
    * @param size the size for the url
    * @return
    */
  def sizedAvatarUrl(size: Int) = avatarUrl + "&s=" + size.toString
}
