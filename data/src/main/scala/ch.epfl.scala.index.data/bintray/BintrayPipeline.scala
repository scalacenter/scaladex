package ch.epfl.scala.index
package data
package bintray

import cleanup.NonStandardLib
import elastic.SeedElasticSearch
import github.GithubDownload
import maven.DownloadParentPoms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object BintrayPipeline {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val pathFromArgs =
      if (args.isEmpty) Nil
      else args.toList.tail

    val paths = DataPaths(pathFromArgs)
    val bintray: LocalRepository = LocalRepository.Bintray

    def list(): Unit = {

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

      println("list done")
    }

    def download(): Unit = {

      val downloadPomsStep = new BintrayDownloadPoms(paths)
      downloadPomsStep.run()

      println("download done")
    }

    def parent(): Unit = {

      val downloadParentPomsStep = new DownloadParentPoms(bintray, paths)
      downloadParentPomsStep.run()

      println("parent done")
    }

    def github(): Unit = {

      val githubDownload = new GithubDownload(paths)
      githubDownload.run()

      println("github done")
    }

    def elastic(): Unit = {

      val seedElasticSearchStep = new SeedElasticSearch(paths)
      seedElasticSearchStep.run()
    }

    val steps = List(
      "list" -> list _,
      "download" -> download _,
      "parent" -> parent _,
      "github" -> github _,
      "elastic" -> elastic _
    )

    val stepsMap = steps.toMap

    (args.toList match {
      case "all" :: _ => steps.map(_._2)
      case _ => stepsMap.get(args.head).toList
    }).foreach(step => step())

    system.terminate()
    ()
  }
}
