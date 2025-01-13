package scaladex.server

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

import scaladex.core.service.ProjectService
import scaladex.data.util.PidLock
import scaladex.infra.DataPaths
import scaladex.infra.ElasticsearchEngine
import scaladex.infra.FilesystemStorage
import scaladex.infra.GithubClientImpl
import scaladex.infra.MavenCentralClientImpl
import scaladex.infra.SqlDatabase
import scaladex.infra.sql.DoobieUtils
import scaladex.server.config.ServerConfig
import scaladex.server.route.*
import scaladex.server.route.api.*
import scaladex.server.service.AdminService
import scaladex.server.service.ArtifactService
import scaladex.server.service.MavenCentralService
import scaladex.server.service.PublishProcess
import scaladex.view.html.notfound

import cats.effect.ContextShift
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import doobie.util.ExecutionContexts
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.*
import org.apache.pekko.http.scaladsl.server.Directives.*

object Server extends LazyLogging:

  def main(args: Array[String]): Unit =
    try
      val config: ServerConfig = ServerConfig.load()

      logger.info(config.filesystem.toString)

      if config.env.isDev || config.env.isProd then PidLock.create("SERVER")
      given system: ActorSystem = ActorSystem("scaladex")
      given ExecutionContext = system.dispatcher
      given ContextShift[IO] = IO.contextShift(system.dispatcher)

      // the ESRepo will not be closed until the end of the process,
      // because of the sbtResolver mode
      val searchEngine = ElasticsearchEngine.open(config.elasticsearch)

      val resources =
        val datasourceWeb = DoobieUtils.getHikariDataSource(config.database)
        val datasourceScheduler = DoobieUtils.getHikariDataSource(config.database)
        for
          webPool <- DoobieUtils.transactor(datasourceWeb)
          schedulerPool <- DoobieUtils.transactor(datasourceScheduler)
          publishPool <- ExecutionContexts.fixedThreadPool[IO](8)
        yield (webPool, schedulerPool, publishPool, datasourceWeb)
      resources
        .use {
          case (webPool, schedulerPool, publishPool, datasourceForFlyway) =>
            val webDatabase = new SqlDatabase(datasourceForFlyway, webPool)
            val schedulerDatabase = new SqlDatabase(datasourceForFlyway, schedulerPool)
            val githubClient = config.github.token.map(new GithubClientImpl(_))
            val paths = DataPaths.from(config.filesystem)
            val filesystem = FilesystemStorage(config.filesystem)
            val publishProcess = PublishProcess(paths, filesystem, webDatabase, config.env)(using publishPool, system)
            val mavenCentralClient = new MavenCentralClientImpl()
            val mavenCentralService =
              new MavenCentralService(paths, schedulerDatabase, mavenCentralClient, publishProcess)
            val adminService =
              new AdminService(config.env, schedulerDatabase, searchEngine, githubClient, mavenCentralService)

            for
              _ <- init(webDatabase, adminService, searchEngine, config.elasticsearch.reset)
              routes = configureRoutes(
                config,
                searchEngine,
                webDatabase,
                adminService,
                publishProcess
              )
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
            yield ()
            end for
        }
        .unsafeRunSync()
    catch
      case NonFatal(exception) =>
        logger.error("Server failed to start", exception)
        sys.exit(1)

  private def init(
      database: SqlDatabase,
      adminService: AdminService,
      searchEngine: ElasticsearchEngine,
      resetElastic: Boolean
  )(
      using ContextShift[IO]
  ): IO[Unit] =
    logger.info("Applying flyway migration to database")
    for
      _ <- database.migrate
      _ = logger.info("Waiting for ElasticSearch to start")
      _ <- IO.fromFuture(IO(searchEngine.init(resetElastic)))
      _ = logger.info("Starting all schedulers")
      _ <- IO(adminService.startAllJobs())
    yield ()
  end init
  private def configureRoutes(
      config: ServerConfig,
      searchEngine: ElasticsearchEngine,
      webDatabase: SqlDatabase,
      adminService: AdminService,
      publishProcess: PublishProcess
  )(
      using system: ActorSystem
  ): Route =
    given ExecutionContext = system.dispatcher

    val githubAuth = GithubAuthImpl(config.oAuth2)

    val projectService = new ProjectService(webDatabase, searchEngine)
    val artifactService = new ArtifactService(webDatabase)
    val searchPages = new SearchPages(config.env, searchEngine)
    val frontPage = new FrontPage(config.env, webDatabase, searchEngine)
    val adminPages = new AdminPage(config.env, adminService)
    val projectPages = new ProjectPages(config.env, projectService, artifactService, webDatabase, searchEngine)
    val artifactPages = new ArtifactPages(config.env, webDatabase)
    val awesomePages = new AwesomePages(config.env, searchEngine)
    val publishApi = new PublishApi(githubAuth, publishProcess)
    val apiEndpoints = new ApiEndpointsImpl(projectService, artifactService, searchEngine)
    val oldSearchApi = new OldSearchApi(searchEngine, webDatabase)
    val badges = new Badges(projectService)
    val authentication = new AuthenticationApi(config.oAuth2.clientId, config.session, githubAuth, webDatabase)

    val route: Route =
      authentication.optionalUser { user =>
        val apiRoute = concat(
          publishApi.routes,
          apiEndpoints.routes(user),
          oldSearchApi.routes,
          Assets.routes,
          badges.route,
          authentication.routes,
          DocumentationRoute.route
        )

        concat(
          apiRoute,
          frontPage.route(user),
          adminPages.route(user),
          awesomePages.route(user),
          artifactPages.route(user),
          redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
            projectPages.route(user) ~ searchPages.route(user)
          }
        )
      }
    val exceptionHandler = ExceptionHandler {
      case NonFatal(cause) =>
        extractUri { uri =>
          import scaladex.server.TwirlSupport.given
          logger.error(s"Unhandled exception in $uri", cause)
          complete(StatusCodes.InternalServerError, notfound(config.env, None))
        }
    }
    handleExceptions(exceptionHandler)(route)
  end configureRoutes
end Server
