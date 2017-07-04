package ch.epfl.scala.index
package server

import routes._
import routes.api._
import data.DataPaths
import data.util.PidLock
import data.elastic._

import TwirlSupport._

import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes.UnprocessableEntity

import com.softwaremill.session._
import SessionDirectives._
import SessionOptions._


import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.duration._
import scala.concurrent.Await

import org.slf4j.LoggerFactory

import org.elasticsearch.action.search.SearchPhaseExecutionException
import org.apache.lucene.queryparser.classic.ParseException

object Server {
  private val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    val port =
      if (args.isEmpty) 8080
      else args.head.toInt

    val config = ConfigFactory.load().getConfig("org.scala_lang.index.server")
    val production = config.getBoolean("production")

    if (production) {
      PidLock.create("SERVER")
    }

    implicit val system = ActorSystem("scaladex")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val pathFromArgs =
      if (args.isEmpty) Nil
      else args.toList.tail

    val paths = DataPaths(pathFromArgs)

    val github = new Github
    val data = new DataRepository(github, paths)
    val session = new GithubUserSession(config)

    import session._

    val searchPages = new SearchPages(data, session)

    val userFacingRoutes =
      concat(
        new FrontPage(data, session).routes,
        redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
          concat(
            new ProjectPages(data, session).routes,
            searchPages.routes
          )
        }
      )

    val programmaticRoutes = concat(
      new PublishApi(paths, data, github).routes,
      new SearchApi(data).routes,
      Assets.routes,
      new Badges(data).routes,
      new OAuth2(github, session).routes
    )

    val exceptionHandler = ExceptionHandler {
      case ex: SearchPhaseExecutionException =>
        optionalSession(refreshable, usingCookies) { userId =>
          searchPages.searchParams(userId) { params =>
            complete(
              (
                UnprocessableEntity,
                views.html.invalidQuery(
                  getUser(userId).map(_.user),
                  params
                )
              )
            )
          }
        }
    }

    val routes = 
      handleExceptions(exceptionHandler){
        concat(programmaticRoutes, userFacingRoutes)
      }


    log.info("waiting for elastic to start")
    blockUntilYellow()
    log.info("ready")

    Await.result(Http().bindAndHandle(routes, "0.0.0.0", port), 20.seconds)

    log.info(s"port: $port")
    log.info("Application started")

    ()
  }
}
