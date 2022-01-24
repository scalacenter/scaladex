package scaladex.core.model.data

sealed trait LocalRepository extends Product with Serializable

object LocalRepository {
  final case object BintraySbtPlugins extends LocalRepository
}

sealed trait LocalPomRepository extends LocalRepository
object LocalPomRepository {
  final case object Bintray extends LocalPomRepository
  final case object MavenCentral extends LocalPomRepository
  final case object UserProvided extends LocalPomRepository
}
