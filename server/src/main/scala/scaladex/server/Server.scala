package scaladex.server

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.effect.ContextShift
import cats.effect.IO
import org.slf4j.LoggerFactory
import scaladex.core.model.Env
import scaladex.core.service.WebDatabase
import scaladex.data.util.PidLock
import scaladex.infra.elasticsearch.ElasticsearchEngine
import scaladex.infra.github.GithubClient
import scaladex.infra.storage.local.LocalStorageRepo
import scaladex.infra.storage.sql.SqlRepo
import scaladex.infra.util.DoobieUtils
import scaladex.server.config.ServerConfig
import scaladex.server.route._
import scaladex.server.route.api._
import scaladex.server.service.SchedulerService

object Server {
  private val log = LoggerFactory.getLogger(getClass)
  val config: ServerConfig = ServerConfig.load()

  def main(args: Array[String]): Unit = {
    if (config.env.isDev || config.env.isProd) {
      PidLock.create("SERVER")
    }
    implicit val system: ActorSystem = ActorSystem("scaladex")
    import system.dispatcher
    implicit val cs = IO.contextShift(system.dispatcher)

    // the ESRepo will not be closed until the end of the process,
    // because of the sbtResolver mode
    val searchEngine = ElasticsearchEngine.open(config.elasticsearch)

    val resources =
      for {
        webPool <- DoobieUtils.transactor(config.database)
        schedulerPool <- DoobieUtils.transactor(config.database)
      } yield (webPool, schedulerPool)

    resources
      .use {
        case (webPool, schedulerPool) =>
          val webDb = new SqlRepo(config.database, webPool)
          val schedulerDb = new SqlRepo(config.database, schedulerPool)
          val githubService = config.github.token.map(new GithubClient(_))
          val schedulerService = new SchedulerService(schedulerDb, searchEngine, githubService)
          for {
            _ <- init(webDb, schedulerService, searchEngine)
            routes = configureRoutes(config.env, searchEngine, webDb, schedulerService)
            _ <- IO(
              Http()
                .bindAndHandle(routes, config.endpoint, config.port)
                .andThen {
                  case Failure(exception) =>
                    log.error("Unable to start the server", exception)
                    System.exit(1)
                  case Success(binding) =>
                    log.info(s"Server started at http://${config.endpoint}:${config.port}")
                    sys.addShutdownHook {
                      log.info("Stopping server")
                      binding.terminate(hardDeadline = 10.seconds)
                    }
                }
            )
            _ <- IO.never
          } yield ()
      }
      .unsafeRunSync()

  }

  private def init(db: SqlRepo, scheduler: SchedulerService, searchEngine: ElasticsearchEngine)(
      implicit cs: ContextShift[IO]
  ): IO[Unit] = {
    log.info("applying flyway migration to db")
    for {
      _ <- db.migrate
      _ = log.info("wait for ElasticSearch")
      _ <- IO(searchEngine.waitUntilReady())
      _ <-
        if (config.elasticsearch.reset) IO.fromFuture(IO(searchEngine.reset()))
        else IO.unit
      _ = log.info("starting the scheduler")
      _ <- IO(scheduler.startAll())
    } yield ()
  }
  private def configureRoutes(
      env: Env,
      searchEngine: ElasticsearchEngine,
      webDb: WebDatabase,
      schedulerService: SchedulerService
  )(
      implicit actor: ActorSystem
  ): Route = {
    import actor.dispatcher
    val paths = config.dataPaths
    val githubAuth = new GithubAuth()
    val session = new GithubUserSession(config.session)
    val searchPages = new SearchPages(env, searchEngine, session)

    val localStorage = new LocalStorageRepo(paths)
    val programmaticRoutes = concat(
      new PublishApi(paths, webDb, githubAuth).routes,
      new SearchApi(searchEngine, webDb, session).routes,
      Assets.routes,
      new Badges(webDb).routes,
      new Oauth2(config.oAuth2, githubAuth, session).routes
    )
    val userFacingRoutes = concat(
      new FrontPage(env, webDb, session).routes,
      new AdminPages(env, schedulerService, session).routes,
      redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
        concat(
          new ProjectPages(env, webDb, localStorage, session, paths).routes,
          searchPages.routes
        )
      }
    )
    val exceptionHandler = ExceptionHandler {
      case ex: Exception =>
        import java.io.{PrintWriter, StringWriter}

        val sw = new StringWriter()
        val pw = new PrintWriter(sw)
        ex.printStackTrace(pw)

        val out = sw.toString

        log.error(out)

        complete(
          StatusCodes.InternalServerError,
          out
        )
    }
    handleExceptions(exceptionHandler) {
      concat(programmaticRoutes, userFacingRoutes)
    }
  }
}
