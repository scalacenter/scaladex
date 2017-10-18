package ch.epfl.scala.index
package data

import ch.epfl.scala.index.model.SemanticVersion
import ch.epfl.scala.index.model.release.ScalaTarget
import ch.epfl.scala.index.model.{Release, Project}
import ch.epfl.scala.index.data.github.GithubDownload
import ch.epfl.scala.index.data.project.ProjectConvert
import ch.epfl.scala.index.data.maven.PomsReader

import scala.util.Success

import scala.concurrent.duration._
import scala.concurrent.Await

import akka.stream.scaladsl._

import org.scalatest._

import org.json4s.native.JsonMethods._

import org.slf4j.LoggerFactory

import CentralStream._

class StreamSuite() extends FunSuite with BeforeAndAfterAll {
  val paths = DataPaths(Nil)
  val githubDownload = new GithubDownload(paths)
  val projectConverter = new ProjectConvert(paths, githubDownload)
  val releases: Set[Release] = projectConverter(
    PomsReader.loadAll(paths).collect {
      case Success(pomAndMeta) => pomAndMeta
    }
  ).map { case (project, releases) => releases }.flatten.toSet

  val scala213 = SemanticVersion("2.13.0-M2").get
  val scala212 = SemanticVersion("2.12").get
  val scala211 = SemanticVersion("2.11").get
  val scala210 = SemanticVersion("2.10").get

  val sbt013 = SemanticVersion("0.13").get
  val sbt10 = SemanticVersion("1.0").get

  val scalaJs06 = SemanticVersion("0.6").get

  val native03 = SemanticVersion("0.3").get

  val targets = List(
    ScalaTarget.scala(scala213),
    ScalaTarget.scala(scala212),
    ScalaTarget.scala(scala211),
    ScalaTarget.scala(scala210),
    ScalaTarget.sbt(scala210, sbt013),
    ScalaTarget.sbt(scala210, sbt10),
    ScalaTarget.scalaJs(scala213, scalaJs06),
    ScalaTarget.scalaJs(scala212, scalaJs06),
    ScalaTarget.scalaJs(scala211, scalaJs06),
    ScalaTarget.scalaJs(scala210, scalaJs06),
    ScalaTarget.scalaNative(scala211, native03)
  )

  val releasesDownloads = releases
    .flatMap(
      release =>
        targets.map(
          target =>
            ArtifactRequest(release.maven.groupId,
                            release.reference.artifact + target.encode)
      )
    )
    .toList

  test("list") {
    val log = LoggerFactory.getLogger(getClass)

    val progress = ProgressBar("Listing", releasesDownloads.size, log)
    progress.start()

    val fetch =
      Source(releasesDownloads)
        .map(ar => (search(ar), ar))
        .via(mavenSearchConnectionPool)
        .via(parseJson)
        .alsoTo(Sink.foreach(_ => progress.step()))
        .runWith(Sink.seq)

    val result =
      Await.result(fetch, 100.minutes)

    progress.stop()

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
          DownloadRequest("com.typesafe.akka",
                          "akka-http-core",
                          "10.0.10",
                          ScalaTarget.scala(SemanticVersion("2.12").get))
        )
      ).map(dr => (downloadR(dr), dr))
        .via(mavenDownloadConnectionPool)
        .via(readContent)
        .runWith(Sink.seq)

    val result =
      Await.result(fetch, 10.seconds)

    println(result)
  }

  override def afterAll {
    system.terminate()
  }
}
