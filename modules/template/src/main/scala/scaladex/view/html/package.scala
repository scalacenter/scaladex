package scaladex.view

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.Uri.Query
import scaladex.core.model.Artifact
import scaladex.core.model.BinaryVersion
import scaladex.core.model.Category
import scaladex.core.model.Project
import scaladex.core.model.search.AwesomeParams
import scaladex.core.model.search.SearchParams
import scaladex.core.web.ArtifactsPageParams

package object html:

  extension (uri: Uri)

    def appendQuery(kv: (String, String)): Uri =
      uri.withQuery(Query((uri.query(java.nio.charset.Charset.forName("UTF-8")) ++ Vector(kv))*))

    def appendQuery(k: String, on: Boolean): Uri = if on then uri.appendQuery(k -> "✓") else uri

    def appendQuery(k: String, vs: Seq[String]): Uri = vs.foldLeft(uri) { case (acc, v) => acc.appendQuery(k -> v) }

    def appendQuery(k: String, vs: Set[String]): Uri = vs.foldLeft(uri) { case (acc, v) => acc.appendQuery(k -> v) }

    def appendQuery(k: String, ov: Option[String]): Uri =
      ov match
        case Some(v) => appendQuery(k -> v)
        case None => uri
  end extension

  def ensureUri(in: String): String =
    if in.startsWith("https://") || in.startsWith("http://") then in
    else "http://" + in

  def paginationUri(params: SearchParams, uri: Uri, you: Boolean)(page: Int): Uri =
    val newUri = uri
      .appendQuery("sort" -> params.sorting.label)
      .appendQuery("topic", params.topics)
      .appendQuery("language", params.languages.map(_.value))
      .appendQuery("platform", params.platforms.map(_.value))
      .appendQuery("you", you)
      .appendQuery("q" -> params.queryString)
      .appendQuery("page" -> page.toString)

    if params.contributingSearch then
      newUri.appendQuery(
        "contributingSearch" -> params.contributingSearch.toString
      )
    else newUri
  end paginationUri

  def paginationUri(category: Category, params: AwesomeParams)(page: Int): Uri =
    Uri(s"/awesome/${category.label}")
      .appendQuery("sort" -> params.sorting.label)
      .appendQuery("language", params.languages.map(_.value))
      .appendQuery("platform", params.platforms.map(_.value))
      .appendQuery("page" -> page.toString)

  def awesomeCategoryUri(category: Category, params: AwesomeParams): Uri =
    Uri(s"/awesome/${category.label}")
      .appendQuery("sort" -> params.sorting.label)
      .appendQuery("language", params.languages.map(_.value))
      .appendQuery("platform", params.platforms.map(_.value))

  def versionsUri(ref: Project.Reference, artifactName: Artifact.Name, params: ArtifactsPageParams): Uri =
    Uri(s"/$ref/artifacts/$artifactName")
      .appendQuery("binary-version", params.binaryVersions.map(_.value))
      .appendQuery(("stable-only", params.stableOnly.toString))

  def versionsUri(ref: Project.Reference, artifactName: Artifact.Name, binaryVersion: Option[BinaryVersion]): Uri =
    Uri(s"/$ref/artifacts/$artifactName")
      .appendQuery("binary-version", binaryVersion.map(_.value))

  def artifactsUri(ref: Project.Reference, params: ArtifactsPageParams): Uri =
    Uri(s"/$ref/artifacts")
      .appendQuery("binary-version", params.binaryVersions.map(_.value))
      .appendQuery(("stable-only", params.stableOnly.toString))

  def artifactsUri(ref: Project.Reference, binaryVersion: Option[BinaryVersion]): Uri =
    Uri(s"/$ref/artifacts")
      .appendQuery("binary-version", binaryVersion.map(_.value))

  // https://www.reddit.com/r/scala/comments/4n73zz/scala_puzzle_gooooooogle_pagination/d41jor5
  def paginationRender(selected: Int, max: Int, toShow: Int = 10): (Option[Int], List[Int], Option[Int]) =
    val min = 1

    if selected == max && max == 1 then (None, List(1), None)
    else if !(min to max).contains(selected) then (None, List(), None)
    else
      require(
        max > 0 && selected > 0 && toShow > 0,
        "all arguments must be positive"
      )

      val window = (max.min(toShow)) / 2
      val left = selected - window
      val right = selected + window

      val (minToShow, maxToShow) =
        if max < toShow then (min, max)
        else
          (left, right) match
            case (l, _) if l < min => (min, min + toShow - 1)
            case (_, r) if r > max => (max - toShow + 1, max)
            case (l, r) => (l, r - 1 + toShow % 2)

      val prev =
        if selected == 1 then None
        else Some(selected - 1)

      val next =
        if selected == max then None
        else Some(selected + 1)

      (prev, (minToShow to maxToShow).toList, next)
    end if
  end paginationRender

  def unescapeBackground(in: String): String =
    play.twirl.api.HtmlFormat
      .escape(in)
      .toString
      .replace("url(&#x27;", "url('")
      .replace("&#x27;)", "')")

  def formatDate(date: String): String =
    import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
    val in = ISODateTimeFormat.dateTime.withOffsetParsed
    val out = DateTimeFormat.forPattern("dd/MM/yyyy")

    out.print(in.parseDateTime(date))

  private val df = DateTimeFormatter
    .ofPattern("dd MMM YYYY 'at' HH:mm '(UTC)'")
    .withZone(ZoneId.systemDefault.normalized())
    .withLocale(Locale.ENGLISH)

  def formatInstant(i: Instant): String = df.format(i)
  def formatInstant(i: Option[Instant]): String = i.map(df.format).getOrElse("_")
end html
