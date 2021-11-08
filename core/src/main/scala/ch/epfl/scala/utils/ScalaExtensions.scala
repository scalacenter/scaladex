package ch.epfl.scala.utils

import scala.collection.BuildFrom
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

object ScalaExtensions {
  implicit class OptionExtension[A](val in: Option[A]) extends AnyVal {
    def toFuture(e: => Throwable): Future[A] = in match {
      case Some(v) => Future.successful(v)
      case None    => Future.failed(e)
    }
    def toTry(e: => Throwable): Try[A] = in match {
      case Some(v) => Success(v)
      case None    => Failure(e)
    }
  }
  implicit class TraversableOnceFutureExtension[
      A,
      CC[X] <: IterableOnce[X],
      To
  ](val in: CC[Future[A]])
      extends AnyVal {
    def sequence(implicit bf: BuildFrom[CC[Future[A]], A, To], executor: ExecutionContext): Future[To] =
      Future.sequence(in)
  }

  implicit class FutureExtension[A](val in: Future[A]) extends AnyVal {
    def mapFailure(f: Throwable => Throwable)(implicit ec: ExecutionContext): Future[A] =
      in.recoverWith { case NonFatal(e) => Future.failed(f(e)) }

    def failWithTry(implicit ec: ExecutionContext): Future[Try[A]] =
      in.map(Success(_)).recover { case NonFatal(e) => Failure(e) }
  }
}
