package ch.epfl.scala.index
package data

import ch.epfl.scala.index.model.release.ScalaTarget
import ch.epfl.scala.index.model.{SemanticVersion, Artifact}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import play.api._

import play.api.libs.ws._
import play.api.libs.ws.ahc._
import play.api.libs.json._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

import org.joda.time.DateTime
import org.json4s._
import org.json4s.native.JsonMethods._

import java.nio.file._

object Scrape {
  object TimeStampDateTimeSerializer
      extends CustomSerializer[DateTime](
        format =>
          (
            {
              case JLong(timestamp)  => new DateTime(timestamp)
              case JInt(timestamp)   => new DateTime(timestamp.toLong)
            }, { case time: DateTime => JLong(time.getMillis) }
        )
      )

  implicit val formats = DefaultFormats ++ Seq(TimeStampDateTimeSerializer)
  implicit val serialization = native.Serialization

  // q=sbt-microsites
  object Latest {
    case class Body(response: Response)
    case class Response(docs: List[Doc])
    case class Doc(
        g: String,
        a: String,
        latestVersion: String,
        timestamp: DateTime
    )
  }

  // q = g:"com.47deg" AND a:"sbt-microsites"
  // core = gav
  object Gav {
    case class Body(response: Response)
    case class Response(docs: List[Doc])
    case class Doc(
        g: String,
        a: String,
        v: String,
        timestamp: DateTime
    )
  }

  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  val wsClient = {
    val configuration = Configuration.reference ++ Configuration(
      ConfigFactory.parseString("plaw.ws.followRedirects = true")
    )

    val environment = Environment(
      new java.io.File("."),
      this.getClass.getClassLoader,
      Mode.Prod
    )

    val wsConfig = AhcWSClientConfigFactory.forConfig(
      configuration.underlying,
      environment.classLoader
    )

    AhcWSClient(wsConfig)
  }

  def requestLatest(querry: String): List[Latest.Doc] = {
    val request =
      wsClient
        .url("https://search.maven.org/solrsearch/select")
        .withQueryStringParameters(
          "q" -> querry
        )
        .get

    parse(Await.result(request, 10.seconds).body)
      .extract[Latest.Body]
      .response
      .docs
  }

  // requestLatest("sbt-microsites")

  def requestGav(groupId: String, artifactId: String): List[Gav.Doc] = {
    val request =
      wsClient
        .url("https://search.maven.org/solrsearch/select")
        .withQueryStringParameters(
          "q" -> s"""g:"$groupId" AND a:"$artifactId"""",
          "core" -> "gav"
        )
        .get

    parse(Await.result(request, 10.seconds).body)
      .extract[Gav.Body]
      .response
      .docs
  }

  // requestGav("com.47deg", "sbt-microsites")

  // https://repo1.maven.org/maven2/com/47deg/sbt-microsites_2.12_1.0/0.7.1/sbt-microsites-0.7.1.pom
  def download(groupId: String,
               artifact: String,
               version: String,
               target: ScalaTarget): String = {
    val path = groupId.replaceAllLiterally(".", "/")
    val request =
      wsClient
        .url(
          s"https://repo1.maven.org/maven2/$path/$artifact${target.encode}/$version/$artifact-$version.pom"
        )
        .get

    Await.result(request, 10.seconds).body
  }

  val sbtTarget =
    ScalaTarget.sbt(SemanticVersion("2.12").get, SemanticVersion("1.0").get)
  // download("com.47deg", "sbt-microsites", "0.7.1", sbtTarget)
  // val lines = Files.readAllLines(Paths.get("../trash/_scaladex-missing")).toArray.toSet.map((x: Any) => x.toString)
  // val maven = lines.map(line => (line, requestLatest(line)))
  // import scala.collection.JavaConverters._
  // val lines = Files.readAllLines(Paths.get("../trash/_scaladex-missing")).asScala.map(_.split('/').tail.head)
}
