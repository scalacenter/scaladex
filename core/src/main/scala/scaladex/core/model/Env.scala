package scaladex.core.model

sealed trait Env {
  def isProd: Boolean = false
  def isDev: Boolean = false
  def isLocal: Boolean = false
  def isDevOrProd: Boolean = isDev || isProd
}
object Env {
  case object Local extends Env {
    override def isLocal: Boolean = true
  }
  case object Dev extends Env {
    override def isDev: Boolean = true
  }
  case object Prod extends Env {
    override def isProd: Boolean = true
  }

  def from(s: String): Env =
    s.toLowerCase match {
      case "local" => Env.Local
      case "prod"  => Env.Prod
      case "dev"   => Env.Dev
      case _       => Env.Prod
    }

}
