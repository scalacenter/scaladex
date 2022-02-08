package scaladex.server

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.effect.ContextShift
import cats.effect.IO
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.typesafe.scalalogging.LazyLogging
import doobie.util.ExecutionContexts
import scaladex.core.service.Storage
import scaladex.core.service.WebDatabase
import scaladex.data.util.PidLock
import scaladex.infra.DataPaths
import scaladex.infra.ElasticsearchEngine
import scaladex.infra.FilesystemStorage
import scaladex.infra.GithubClient
import scaladex.infra.SqlDatabase
import scaladex.infra.sql.DoobieUtils
import scaladex.server.config.ServerConfig
import scaladex.server.route._
import scaladex.server.route.api._
import scaladex.server.service.PublishProcess
import scaladex.server.service.SchedulerService

object Server extends LazyLogging {

  def main(args: Array[String]): Unit =
    try {
      val config: ServerConfig = ServerConfig.load()

      logger.info(config.filesystem.toString)

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
          publishPool <- ExecutionContexts.fixedThreadPool[IO](8)
        } yield (webPool, schedulerPool, publishPool)

      resources
        .use {
          case (webPool, schedulerPool, publishPool) =>
            val webDatabase = new SqlDatabase(config.database, webPool)
            val schedulerDatabase = new SqlDatabase(config.database, schedulerPool)
            val githubService = config.github.token.map(new GithubClient(_))
            val schedulerService = new SchedulerService(schedulerDatabase, searchEngine, githubService)
            val paths = DataPaths.from(config.filesystem)
            val filesystem = FilesystemStorage(config.filesystem)
            val publishProcess = PublishProcess(paths, filesystem, webDatabase)(publishPool)
            for {
              _ <- init(webDatabase, schedulerService, searchEngine, config.elasticsearch.reset)
              routes = configureRoutes(config, searchEngine, webDatabase, schedulerService, filesystem, publishProcess)
              _ <- IO(
                Http()
                  .newServerAt(config.endpoint, config.port)
                  .bindFlow(routes)
                  .andThen {
                    case Failure(exception) =>
                      logger.error("Unable to start the server", exception)
                      System.exit(1)
                    case Success(binding) =>
                      logger.info(s"Server started at http://${config.endpoint}:${config.port}")
                      sys.addShutdownHook {
                        logger.info("Stopping server")
                        binding.terminate(hardDeadline = 10.seconds)
                      }
                  }
              )
              _ <- IO.never
            } yield ()
        }
        .unsafeRunSync()
    } catch {
      case NonFatal(exception) =>
        logger.error("Server failed to start", exception)
        sys.exit(1)
    }

  private def init(
      database: SqlDatabase,
      scheduler: SchedulerService,
      searchEngine: ElasticsearchEngine,
      resetElastic: Boolean
  )(
      implicit cs: ContextShift[IO]
  ): IO[Unit] = {
    logger.info("Applying flyway migration to database")
    for {
      _ <- database.migrate
      _ = logger.info("Waiting for ElasticSearch to start")
      _ <- IO(searchEngine.waitUntilReady())
      _ <-
        if (resetElastic) IO.fromFuture(IO(searchEngine.reset()))
        else IO.unit
      _ = logger.info("Starting all schedulers")
      _ <- IO(scheduler.startAll())
    } yield ()
  }
  private def configureRoutes(
      config: ServerConfig,
      searchEngine: ElasticsearchEngine,
      webDatabase: WebDatabase,
      schedulerService: SchedulerService,
      filesystem: Storage,
      publishProcess: PublishProcess
  )(
      implicit actor: ActorSystem
  ): Route = {
    import actor.dispatcher

    val githubAuth = new GithubAuthImpl(config.env)
    val session = new GithubUserSession(config.session)

    val searchPages = new SearchPages(config.env, searchEngine)
    val frontPage = new FrontPage(config.env, webDatabase, searchEngine)
    val adminPages = new AdminPage(config.env, schedulerService)
    val projectPages = new ProjectPages(config.env, webDatabase, filesystem)
    val explorePages = new ExplorePages(config.env, searchEngine)

    val programmaticRoutes = concat(
      new PublishApi(githubAuth, publishProcess).routes,
      new SearchApi(searchEngine, webDatabase, session).routes,
      Assets.routes,
      new Badges(webDatabase).route,
      new Oauth2(config.oAuth2, githubAuth, session).routes,
      DocumentationRoutes.routes
    )
    import session.implicits._
    val userFacingRoute: Route =
      optionalSession(refreshable, usingCookies) { userId =>
        val user = userId.flatMap(session.getUser)
        frontPage.route(user) ~ adminPages.route(user) ~ explorePages.route(user) ~
          redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
            projectPages.route(user) ~ searchPages.route(user)
          }
      }
    val exceptionHandler = ExceptionHandler {
      case ex: Exception =>
        import java.io.{PrintWriter, StringWriter}

        val sw = new StringWriter()
        val pw = new PrintWriter(sw)
        ex.printStackTrace(pw)

        val out = sw.toString

        logger.error(out)

        complete(
          StatusCodes.InternalServerError,
          out
        )
    }
    handleExceptions(exceptionHandler)(programmaticRoutes ~ userFacingRoute)
  }
}
