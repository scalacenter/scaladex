package scaladex.core.model

object Sha1 {
  def apply(in: String): String = {
    val md = java.security.MessageDigest.getInstance("SHA-1")
    md.digest(in.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}
