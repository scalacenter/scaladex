package ch.epfl.scala.index
package api

import model._

import scala.concurrent.Future

trait Api {
  def userInfo(): Option[UserInfo]
  def autocomplete(q: String): Future[List[(String, String, String)]]
}
