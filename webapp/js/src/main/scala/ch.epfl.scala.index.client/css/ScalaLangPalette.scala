package ch.epfl.scala.index
package client
package css

import scalacss._, Defaults._

// scala-lang.org Theme
class ScalaLangPalette(implicit r: mutable.Register) extends StyleSheet.Inline()(r) {
  import dsl._

  val callToAction = c"#859900"
  val callToActionHover = c"#DC322F"

  val darkerBlue = c"#002B36"
  val darkBlue = c"#073642"
  val teal = c"#839496" // font ^

  val lightBlue = c"#72D0EB"
  val dark = c"#174753"

  val fontMuseo = font

  val fontMuseoItalic = font

  // buttons: 63px height + 40px radius
}