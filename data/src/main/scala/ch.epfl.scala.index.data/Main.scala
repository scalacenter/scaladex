package ch.epfl.scala.index
package data

import bintray._
import github._
import elastic._
import cleanup._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object Main extends BintrayProtocol {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem()
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    def claims(): Unit = {

      val githubRepoExtractor = new GithubRepoExtractor
      githubRepoExtractor.run()
    }

    def list(): Unit = {

      val listPomsStep = new ListPoms
      // TODO: should be located in a config file
      val versions = List("2.12", "2.11", "2.10")

      for(version <- versions) {

        listPomsStep.run(version)
      }

      /* do a search for non standard lib poms */
      for (lib <-  uniqueNonStandardLibs) {

        listPomsStep.run(lib.groupId, lib.artifactId)
      }
    }

    def download(): Unit = {

      val downloadPomsStep = new DownloadPoms
      downloadPomsStep.run()
    }

    def parent(): Unit = {

      val downloadParentPomsStep = new DownloadParentPoms
      downloadParentPomsStep.run()
    }

    def github(): Unit = {

      val githubDownload = new GithubDownload(None, system, materializer)
      githubDownload.run()
    }

    def elastic(): Unit = {

      val seedElasticSearchStep = new SeedElasticSearch
      seedElasticSearchStep.run()
    }

    val steps = List(
      "list"     -> list _,
      "download" -> download _,
      "parent"   -> parent _,
      "github"   -> github _,
      "elastic"  -> elastic _
    )

    val stepsMap = steps.toMap

    (args.toList match {

      case "all" :: Nil => steps.map(_._2)
      case "claims" :: Nil => List(claims _)
      case _ => args.toList.flatMap(stepsMap.get)
    }).foreach(step => step())

    system.terminate()
    ()
  }
}