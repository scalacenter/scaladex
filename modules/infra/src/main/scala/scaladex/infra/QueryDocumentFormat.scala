package scaladex.infra

trait QueryDocumentFormat {

  def fieldAccess(name: String): String =
    s"doc['$name'].value"

  def fieldAccess(name: String, default: String): String =
    s"doc['$name'].value != null ? doc['$name'].value : $default"
}
