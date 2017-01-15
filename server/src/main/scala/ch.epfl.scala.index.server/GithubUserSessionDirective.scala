package ch.epfl.scala.index.server

import akka.http.scaladsl.server.Directive1
import com.softwaremill.session.SessionDirectives.optionalSession
import com.softwaremill.session.SessionOptions.{refreshable, usingCookies}
import java.util.UUID

object GithubUserSessionDirective {
  def githubSession(session: GithubUserSession): Directive1[Option[UUID]] = {
    import session._
    optionalSession(refreshable, usingCookies)
  }
}
