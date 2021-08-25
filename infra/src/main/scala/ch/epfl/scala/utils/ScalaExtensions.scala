package ch.epfl.scala.utils

import scala.collection.BuildFrom
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object ScalaExtensions {
  implicit class OptionExtension[A](val in: Option[A]) extends AnyVal {
    def toFuture(e: => Throwable): Future[A] = in match {
      case Some(v) => Future.successful(v)
      case None => Future.failed(e)
    }
  }
  implicit class TraversableOnceFutureExtension[
      A,
      CC[X] <: IterableOnce[X],
      To
  ](val in: CC[Future[A]])
      extends AnyVal {
    def sequence(implicit
        bf: BuildFrom[CC[Future[A]], A, To],
        executor: ExecutionContext
    ): Future[To] = Future.sequence(in)
  }
}
