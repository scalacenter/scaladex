package scaladex.view

sealed trait ProjectTab
object ProjectTab:
  object Main extends ProjectTab
  object Artifacts extends ProjectTab
  object Versions extends ProjectTab
  object Badges extends ProjectTab
  object Settings extends ProjectTab
  object VersionMatrix extends ProjectTab
