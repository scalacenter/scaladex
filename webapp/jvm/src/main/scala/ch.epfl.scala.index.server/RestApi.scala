package ch.epfl.scala.index
package server

import model.Artifact

import akka.http.scaladsl._
import server.Directives._

import de.heikoseeberger.akkahttpupickle.UpickleSupport

class RestApi(sharedApi: ApiImplementation) extends UpickleSupport {
  val route = get {
    pathPrefix("api") {
      path("find") {
        parameters('query, 'start.as[Int] ? 0) { (query, start) =>
          complete(sharedApi.find(query, start))
        }
      } ~
      path("latest"){
        parameters('organization, 'name) { (organization, name) =>
          complete(sharedApi.latest(Artifact.Reference(organization, name)))
        }
      }
    }  
  }
}