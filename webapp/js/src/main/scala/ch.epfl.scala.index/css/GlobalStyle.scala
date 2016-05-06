package ch.epfl.scala.index
package css

import scalacss.Defaults._

object GlobalStyle extends StyleSheet.Inline {
  import dsl._

  val scalaLang = new ScalaLangPalette

  style(unsafeRoot("body")(
    margin.`0`,
    padding.`0`,
    backgroundColor(scalaLang.darkerBlue)
  ))
}
