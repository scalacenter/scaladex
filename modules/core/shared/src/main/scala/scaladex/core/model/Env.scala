package scaladex.core.model

sealed trait Env:
  def isProd: Boolean = false
  def isDev: Boolean = false
  def isLocal: Boolean = false
  def isDevOrProd: Boolean = isDev || isProd
  def rootUrl: String
object Env:
  case object Local extends Env:
    override def isLocal: Boolean = true
    override def rootUrl: String = "http://localhost:8080"
  case object Dev extends Env:
    override def isDev: Boolean = true
    override def rootUrl: String = "https://index-dev.scala-lang.org"
  case object Prod extends Env:
    override def isProd: Boolean = true
    override def rootUrl: String = "https://index.scala-lang.org"

  def from(s: String): Env =
    s.toLowerCase match
      case "local" => Env.Local
      case "prod" => Env.Prod
      case "dev" => Env.Dev
      case _ => Env.Prod
end Env
