package ch.epfl.scala.index
package data
package bintray

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

trait BintrayCredentials {
  private val bintray = {
    // from bintray-sbt convention
    // cat ~/.bintray/.credentials
    // host = api.bintray.com
    // user = xxxxxxxxxx
    // password = xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

    val home = System.getProperty("user.home")
    val path = home + "/.bintray/.credentials2"
    val nl = System.lineSeparator
    val source = io.Source.fromFile(path)

    val info =
      source.mkString.split(nl).map{ v =>
        val (l, r) = v.span(_ != '=' )
        (l.trim, r.drop(2).trim)
      }.toMap
    source.close()

    info
  }

  def withAuthorization(request: HttpRequest) = {
    (bintray.get("user"), bintray.get("password")) match {
      case (Some(user), Some(key)) => request.withHeaders(Authorization(BasicHttpCredentials(user, key)))
      case _ => request
    }
  }
}