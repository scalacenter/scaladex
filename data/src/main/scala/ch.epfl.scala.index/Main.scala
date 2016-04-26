package ch.epfl.scala.index

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object Main {
  def main(args: Array[String]): Unit = {
    val (list, download, parent) = args.toList match {
      case "list" :: Nil     => ( true, false, false)
      case "download" :: Nil => (false,  true, false)
      case "parent" :: Nil   => (false, false,  true)
      case _                 => ( true,  true,  true)
    }

    import bintray._
    import maven._

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

    system.terminate()
    ()
  }
}