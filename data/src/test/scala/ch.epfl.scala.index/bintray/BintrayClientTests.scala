package ch.epfl.scala.index.bintray

import akka.stream.scaladsl.Source
import akka.util.ByteString
import ch.epfl.scala.index.data.bintray.BintrayClient
import org.scalatest._
import play.api.libs.json.JsValue
import play.api.libs.ws.{WSCookie, WSResponse}

import scala.xml.Elem

class BintrayClientTests extends FlatSpec with Matchers {
  "BintrayClient" should "calculate pagination properly" in {
    getPagination(startPos = 50, endPos = 59, total = 100) should contain theSameElementsAs List(
      60,
      70,
      80,
      90
    )
    getPagination(startPos = 0, endPos = 49, total = 100) should contain theSameElementsAs List(
      50
    )
    getPagination(startPos = 50, endPos = 99, total = 100) should contain theSameElementsAs Nil
    getPagination(startPos = 0, endPos = 49, total = 50) should contain theSameElementsAs Nil
    getPagination(startPos = 0, endPos = 49, total = 51) should contain theSameElementsAs List(
      50
    )
    getPagination(startPos = 0, endPos = 0, total = 10) should contain theSameElementsAs List(
        1, 2, 3, 4, 5, 6, 7, 8, 9)
  }

  def getPagination(startPos: Int, endPos: Int, total: Int): Seq[Int] = {
    val wsResponse = wsResponseWithHeaders(
      Map("X-RangeLimit-Total" -> Seq(total.toString),
          "X-RangeLimit-StartPos" -> Seq(startPos.toString),
          "X-RangeLimit-EndPos" -> Seq(endPos.toString))
    )

    BintrayClient.remainingPages(wsResponse)
  }

  private def wsResponseWithHeaders(providedHeaders: Map[String, Seq[String]]) =
    new WSResponse {
      override def status: Int = ???

      override def statusText: String = ???

      override def underlying[T]: T = ???

      override def cookies: Seq[WSCookie] = ???

      override def cookie(name: String): Option[WSCookie] = ???

      override def body: String = ???

      override def bodyAsBytes: ByteString = ???

      override def bodyAsSource: Source[ByteString, _] = ???

      override def allHeaders: Map[String, Seq[String]] = ???

      override def xml: Elem = ???

      override def json: JsValue = ???

      override def headers: Map[String, Seq[String]] = providedHeaders
    }
}
