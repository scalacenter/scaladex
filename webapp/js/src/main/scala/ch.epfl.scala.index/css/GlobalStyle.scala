package ch.epfl.scala.index
package css

import scalacss.Defaults._
import scalacss.ext.CssReset

object GlobalStyle extends StyleSheet.Inline {
  import dsl._

  style(unsafeRoot("body")(
    margin.`0`,
    padding.`0`
  )) + style(CssReset.normaliseCss)
}
