package ch.epfl.scala.index
package data

import bintray._
import github._
import elastic._
import cleanup._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object Main {

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
      val versions = List("2.10", "2.11", "2.12")

      for(version <- versions) {

        println(s"fetch scala version $version")
        listPomsStep.run(version)
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

      val githubDownload = new GithubDownload
      githubDownload.run()
    }

    def elastic(): Unit = {

      val seedElasticSearchStep = new SeedElasticSearch
      seedElasticSearchStep.run()
    }

    val steps = Map(
      "list"     -> list _,
      "download" -> download _,
      "parent"   -> parent _,
      "github"   -> github _,
      "elastic"  -> elastic _
    )

    (args.toList match {

      case "all" :: Nil => steps.values
      case "claims" :: Nil => List(claims _)
      case _ => args.toList.flatMap(steps.get)
    }).foreach(step => step())

    system.terminate()
    ()
  }
}