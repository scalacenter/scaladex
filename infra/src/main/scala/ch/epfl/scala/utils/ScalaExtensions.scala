package ch.epfl.scala.utils

import scala.concurrent.Future

object ScalaExtensions {
  implicit class OptionExtension[A](val in: Option[A]) extends AnyVal {
    def toFuture(e: => Throwable): Future[A] = in match {
      case Some(v) => Future.successful(v)
      case None => Future.failed(e)
    }
  }
}
