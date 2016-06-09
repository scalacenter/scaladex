package ch.epfl.scala.index
package views

import model.Url

package object html {
  def trimProtocol(url: Url): String = {
    val u = url.target
    val http = "http://"
    val https = "https://"

    if(u.startsWith(http)) u.drop(http.length)
    else if(u.startsWith(https)) u.drop(https.length)
    else u
  }
}