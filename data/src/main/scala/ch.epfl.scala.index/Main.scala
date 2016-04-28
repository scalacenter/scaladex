package ch.epfl.scala.index

import bintray._
import maven._
import github._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object Main {
  def main(args: Array[String]): Unit = {
    val (list, download, parent, github) = args.toList match {
      case "list" :: Nil     => ( true, false, false, false)
      case "download" :: Nil => (false,  true, false, false)
      case "parent" :: Nil   => (false, false,  true, false)
      case "github" :: Nil   => (false, false, false,  true)
      case _                 => ( true,  true,  true,  true)
    }

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    if(list) {
      val listPomsStep = new ListPoms
      listPomsStep.run()
    }

    if(download) {
      val downloadPomsStep = new DownloadPoms
      downloadPomsStep.run()
    }

    if(parent) {
      val downloadParentPomsStep = new DownloadParentPoms
      downloadParentPomsStep.run()
    }

    if(github) {
      val downloadReadMeStep = new DownloadReadMe
      downloadReadMeStep.run()
    }

    system.terminate()
    ()
  }
}