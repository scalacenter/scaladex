package ch.epfl.scala.index.server

import akka.http.scaladsl.model.headers.HttpCredentials
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.server.directives.SecurityDirectives.AsyncAuthenticator
import ch.epfl.scala.index.data.github.GithubCredentials

import scala.concurrent.{ExecutionContext, Future}

object GithubAuthenticator{
  /**
    * verifying a login to github
    *
    * @param github
    * @param credentialsHeader the credentials
    * @return
    */
  def apply(github: Github, credentialsHeader: Option[HttpCredentials])
           (implicit executionContext: ExecutionContext): AsyncAuthenticator[(GithubCredentials, UserState)] = {

    // TODO: Is there anyway to use the provided credentials here?
    // Doing so would get rid of the awkward usage of needing to extract HttpCredentials ourselves and
    // create an Authenticator based on them
    case Credentials.Provided(username) => {
      credentialsHeader match {
        case Some(cred) => {
          val upw = new String(new sun.misc.BASE64Decoder().decodeBuffer(cred.token()))
          val userPass = upw.split(":")

          val token = userPass(1)
          val credentials = GithubCredentials(token)
          // todo - catch errors

          github.getUserStateWithToken(token).map(user => Some((credentials, user)))
        }
        case _ => Future.successful(None)
      }
    }
    case _ => Future.successful(None)
  }
}
