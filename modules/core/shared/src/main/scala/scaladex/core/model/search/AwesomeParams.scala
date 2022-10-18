package scaladex.core.model.search

import scaladex.core.model.Language
import scaladex.core.model.Platform

final case class AwesomeParams(
    languages: Seq[Language],
    platforms: Seq[Platform],
    sorting: Sorting
)
