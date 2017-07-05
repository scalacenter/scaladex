package ch.epfl.scala.index.model
package release


object ScalaTargetOrdering extends org.specs2.mutable.Specification {
  "Ordering Scala Targets" >> {

    val s210 = SemanticVersion("2.10.6").get
    val s211 = SemanticVersion("2.11.11").get
    val s212 = SemanticVersion("2.12.2").get
    val js067 = SemanticVersion("0.6.7").get
    val js0618 = SemanticVersion("0.6.18").get
    val nat03 = SemanticVersion("0.3.0").get

    val obtained = List(
      ScalaTarget.scalaJs(s211, js067),
      ScalaTarget.scalaJs(s212, js0618),
      ScalaTarget.scalaJs(s210, js067),
      ScalaTarget.scalaJs(s211, js0618),
      ScalaTarget.scalaJs(s210, js0618),
      ScalaTarget.scalaNative(s211, nat03),
      ScalaTarget.scala(s212),
      ScalaTarget.scala(s211),
      ScalaTarget.scala(s210)
    ).sorted

    val expected = List(
      ScalaTarget.scalaNative(s211, nat03),
      ScalaTarget.scalaJs(s210, js067),
      ScalaTarget.scalaJs(s210, js0618),
      ScalaTarget.scalaJs(s211, js067),
      ScalaTarget.scalaJs(s211, js0618),
      ScalaTarget.scalaJs(s212, js0618),
      ScalaTarget.scala(s210),
      ScalaTarget.scala(s211),
      ScalaTarget.scala(s212),
    )

    obtained ==== expected
  }
}