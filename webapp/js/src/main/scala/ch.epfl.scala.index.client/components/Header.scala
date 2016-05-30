package ch.epfl.scala.index
package client
package components

import css._

import japgolly.scalajs.react._, vdom.all._
import japgolly.scalajs.react.extra.router._

import scalacss.Defaults._
import scalacss.ScalaCssReact._

object Header {
  object Style extends StyleSheet.Inline {
    import dsl._

    val scalaLang = new ScalaLangPalette

    val header = style(
      color.white,
      backgroundColor(scalaLang.darkerBlue),
      padding(20.px),
      flexWrap.wrap,
      flexDirection.row,
      alignItems.stretch,
      display.flex,
      alignContent.spaceBetween,
      justifyContent.spaceBetween
    )

    val logo = style(
      order(1),
      flexGrow(1),
      alignSelf.flexStart
    )

    val search = style(
      order(2),
      flexGrow(10),
      alignSelf.flexStart
    )

    val user = style(
      order(3),
      alignSelf.flexStart
    )
  }

  val component = ReactComponentB[RouterCtl[Page]]("Header")
    .render_P( ctl =>
      header(Style.header)(
        div(Style.logo)(
          img(src := "/assets/scala-logo-white.png", alt := "white scala logo")
        ),
        div(Style.search)(Search(ctl)),
        div(Style.user)(User())
      )
    ).build
}