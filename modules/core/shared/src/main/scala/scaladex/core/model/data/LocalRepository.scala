package scaladex.core.model.data

sealed trait LocalRepository extends Product with Serializable

object LocalRepository {
  case object BintraySbtPlugins extends LocalRepository
}

sealed trait LocalPomRepository extends LocalRepository
object LocalPomRepository {
  case object Bintray extends LocalPomRepository
  case object MavenCentral extends LocalPomRepository
  case object UserProvided extends LocalPomRepository
}
