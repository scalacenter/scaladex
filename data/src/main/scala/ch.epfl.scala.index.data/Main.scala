package ch.epfl.scala.index.data

import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import ch.epfl.scala.index.data.bintray.{BintrayDownloadPoms, BintrayListPoms}
import ch.epfl.scala.index.data.cleanup.NonStandardLib
import ch.epfl.scala.index.data.elastic.SeedElasticSearch
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.maven.DownloadParentPoms
import org.slf4j.LoggerFactory

import scala.sys.process.Process

/**
  * This application manages indexed POMs.
  */
object Main {

  private val logger = LoggerFactory.getLogger("ch.epfl.scala.index.data")

  /**
    * Update data:
    *  - pull the latest data from the 'contrib' repository
    *  - download data from Bintray and update the ElasticSearch index
    *  - commit the new state of the 'index' repository
    *
    * @param args 3 arguments:
    *              - Name of a step to execute (or “all” to execute all the steps)
    *              - Path of the 'contrib' Git repository
    *              - Path of the 'index' Git repository
    */
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val pathFromArgs =
      if (args.isEmpty) Nil
      else args.toList.tail

    val paths = DataPaths(pathFromArgs.take(2))
    val bintray: LocalRepository = LocalRepository.Bintray

    val steps = List(
      // List POMs of Bintray
      Step("list")({ () =>
        val listPomsStep = new BintrayListPoms(paths)

        // TODO: should be located in a config file
        val versions = List("2.12", "2.11", "2.10")

        for (version <- versions) {
          listPomsStep.run(version)
        }

        /* do a search for non standard lib poms */
        for (lib <- NonStandardLib.load(paths)) {
          listPomsStep.run(lib.groupId, lib.artifactId)
        }
      }),

      // Download POMs from Bintray
      Step("download")(() => new BintrayDownloadPoms(paths).run()),

      // Download parent POMs
      Step("parent")(() => new DownloadParentPoms(bintray, paths).run()),

      // Download additional information about projects from Github
      Step("github")(() => new GithubDownload(paths).run()),

      // Re-create the ElasticSearch index
      Step("elastic")(() => new SeedElasticSearch(paths).run())
    )

    val stepsToRun =
      args.headOption match {
        case Some("all") => steps
        case Some(name)  =>
          steps.find(_.name == name)
            .fold(sys.error(s"Unknown step: $name. Available steps are: ${steps.map(_.name).mkString(" ")}."))(List(_))
        case None        => sys.error(s"No step to execute. Available steps are: ${steps.map(_.name).mkString(" ")}.")
      }

    inPath(paths.contrib) { sh =>
      logger.info("Pulling the latest data from the 'contrib' repository")
      sh.exec("git", "checkout", "master")
      sh.exec("git", "remote", "update")
      sh.exec("git", "pull", "origin", "master")
    }

    logger.info("Executing steps")
    stepsToRun.foreach(_.run())

    inPath(paths.index) { sh =>
      logger.info("Saving the updated state to the 'index' repository")
      sh.exec("git", "add", "-A")
      def now() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
      sh.exec("git", "commit", "--allow-empty", "-m", '"' +: now() :+ '"')
    }

    system.terminate()
    ()
  }

  class Step(val name: String)(effect: () => Unit) {
    def run(): Unit = {
      logger.debug(s"Starting $name")
      effect()
      logger.debug(s"$name done")
    }
  }

  object Step {
    def apply(name: String)(effect: () => Unit): Step = new Step(name)(effect)
  }

  def inPath(path: Path)(f: Sh => Unit): Unit = f(new Sh(path))

  class Sh(path: Path) {
    def exec(args: String*): Unit = {
      val process = Process(args, path.toFile)
      val status = process.!
      if (status == 0) ()
      else sys.error(s"Command '${args.mkString(" ")}' exited with status $status")
    }
  }

}
