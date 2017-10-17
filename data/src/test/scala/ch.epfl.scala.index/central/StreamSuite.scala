package ch.epfl.scala.index
package data

import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.ScalaTarget

import scala.concurrent.duration._
import scala.concurrent.Await

import akka.stream.scaladsl._

import org.scalatest.FunSuite

import org.json4s.native.JsonMethods._

import CentralStream._

class StreamSuite() extends FunSuite {
  test("list") {
    // val fetch = 
    //   Source(
    //     List(
    //       ArtifactRequest("com.typesafe.akka", "akka-http-core_2.12"),
    //       ArtifactRequest("com.typesafe.akka", "akka-http-core_2.11")
    //     )
    //   )
    //   .map(ar => (search(ar), ar))
    //   .via(mavenSearchConnectionPool)
    //   .via(parseJson)
    //   .runWith(Sink.seq)

    // val result = 
    //   Await.result(fetch, 10.seconds)

    // Right((
    //   Body(Response(List(
    //     Doc(com.typesafe.akka, akka-http-core_2.11, 10.0.10,           2017-08-31T16:37:12.000+02:00), 
    //     Doc(com.typesafe.akka, akka-http-core_2.11, 10.0.9,            2017-06-26T17:14:54.000+02:00), 
    //     Doc(com.typesafe.akka, akka-http-core_2.11, 10.0.8,            2017-06-20T18:37:59.000+02:00), 
    //     Doc(com.typesafe.akka, akka-http-core_2.11, 10.0.7,            2017-05-25T17:58:00.000+02:00), 
    //     Doc(com.typesafe.akka, akka-http-core_2.11, 10.0.6+7-e2ba6752, 2017-05-23T05:17:38.000+02:00), 
    //     Doc(com.typesafe.akka, akka-http-core_2.11, 10.0.6,            2017-05-03T15:27:12.000+02:00), 
    //     Doc(com.typesafe.akka, akka-http-core_2.11, 2.4.11.2,          2017-05-03T14:44:27.000+02:00), 
    //     Doc(com.typesafe.akka, akka-http-core_2.11, 10.0.5,            2017-03-18T12:10:16.000+01:00), 
    //     Doc(com.typesafe.akka, akka-http-core_2.11, 10.0.4,            2017-02-23T18:00:51.000+01:00), 
    //     Doc(com.typesafe.akka, akka-http-core_2.11, 10.0.3,            2017-01-26T18:40:04.000+01:00)))
    //   ),
    //   ArtifactRequest(com.typesafe.akka,akka-http-core_2.11)
    // ))
  }

  test("download") {
    val fetch = 
      Source(
        List(
          DownloadRequest("com.typesafe.akka", "akka-http-core", "10.0.10", ScalaTarget.scala(SemanticVersion("2.12").get))
        )
      )
      .map(dr => (downloadR(dr), dr))
      .via(mavenDownloadConnectionPool)
      .via(readContent)
      .runWith(Sink.seq)

    val result = 
      Await.result(fetch, 10.seconds)

    println(result)
  }
}