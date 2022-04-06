package scaladex.core.model

case class ReleaseDependency(
    source: Release,
    target: Release,
    scope: String
)
