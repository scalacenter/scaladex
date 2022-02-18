package scaladex.view

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import scaladex.core.model.search.Pagination
import scaladex.core.model.search.SearchParams

package object html {

  implicit class QueryAppend(uri: Uri) {

    def appendQuery(kv: (String, String)): Uri = uri.withQuery(
      Query(
        (uri
          .query(java.nio.charset.Charset.forName("UTF-8")) ++ Vector(kv)): _*
      )
    )

    def appendQuery(k: String, on: Boolean): Uri =
      if (on) uri.appendQuery(k -> "✓")
      else uri
    def appendQuery(k: String, vs: Seq[String]): Uri =
      vs.foldLeft(uri) {
        case (acc, v) =>
          acc.appendQuery(k -> v)
      }
    def appendQuery(k: String, ov: Option[String]): Uri =
      ov match {
        case Some(v) => appendQuery(k -> v)
        case None    => uri
      }
  }

  def ensureUri(in: String): String =
    if (in.startsWith("https://") || in.startsWith("http://")) in
    else "http://" + in

  def paginationUri(
      params: SearchParams,
      uri: Uri,
      pagination: Pagination,
      you: Boolean
  ): Int => Uri = page => {

    val newUri = uri
      .appendQuery("sort" -> params.sorting.label)
      .appendQuery("topics", params.topics)
      .appendQuery("languages", params.languages)
      .appendQuery("patforms", params.platforms)
      .appendQuery("you", you)
      .appendQuery("q" -> params.queryString)
      .appendQuery("page" -> page.toString)

    if (params.contributingSearch)
      newUri.appendQuery(
        "contributingSearch" -> params.contributingSearch.toString
      )
    else newUri

  }

  // https://www.reddit.com/r/scala/comments/4n73zz/scala_puzzle_gooooooogle_pagination/d41jor5
  def paginationRender(
      selected: Int,
      max: Int,
      toShow: Int = 10
  ): (Option[Int], List[Int], Option[Int]) = {
    val min = 1

    if (selected == max && max == 1) (None, List(1), None)
    else if (!(min to max).contains(selected)) (None, List(), None)
    else {
      require(
        max > 0 && selected > 0 && toShow > 0,
        "all arguments must be positive"
      )

      val window = (max.min(toShow)) / 2
      val left = selected - window
      val right = selected + window

      val (minToShow, maxToShow) =
        if (max < toShow) (min, max)
        else {
          (left, right) match {
            case (l, r) if l < min => (min, min + toShow - 1)
            case (l, r) if r > max => (max - toShow + 1, max)
            case (l, r)            => (l, r - 1 + toShow % 2)
          }
        }

      val prev =
        if (selected == 1) None
        else Some(selected - 1)

      val next =
        if (selected == max) None
        else Some(selected + 1)

      (prev, (minToShow to maxToShow).toList, next)
    }
  }

  def unescapeBackground(in: String): String =
    play.twirl.api.HtmlFormat
      .escape(in)
      .toString
      .replace("url(&#x27;", "url('")
      .replace("&#x27;)", "')")

  def formatDate(date: String): String = {
    import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
    val in = ISODateTimeFormat.dateTime.withOffsetParsed
    val out = DateTimeFormat.forPattern("dd/MM/yyyy")

    out.print(in.parseDateTime(date))
  }

  private val df = DateTimeFormatter
    .ofPattern("dd MMM YYYY 'at' HH:mm '(UTC)'")
    .withZone(ZoneId.systemDefault.normalized())
    .withLocale(Locale.ENGLISH)

  def formatInstant(i: Instant): String = df.format(i)
}
