package scaladex.core.model.search

import scaladex.core.model.Language
import scaladex.core.model.Platform
import scaladex.core.model.Stack

final case class AwesomeParams(
    languages: Seq[Language],
    platforms: Seq[Platform],
    stacks: Seq[Stack],
    sorting: Sorting
)
