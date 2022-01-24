package scaladex.core.util

import scala.collection.BuildFrom
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

object ScalaExtensions {
  implicit class IterableOnceFutureExtension[A, CC[X] <: IterableOnce[X], To](val in: CC[Future[A]]) extends AnyVal {
    def sequence(implicit bf: BuildFrom[CC[Future[A]], A, To], executor: ExecutionContext): Future[To] =
      Future.sequence(in)
  }

  implicit class FutureExtension[A](val in: Future[A]) extends AnyVal {
    def mapFailure(f: Throwable => Throwable)(implicit ec: ExecutionContext): Future[A] =
      in.recoverWith { case NonFatal(e) => Future.failed(f(e)) }

    def failWithTry(implicit ec: ExecutionContext): Future[Try[A]] =
      in.map(Success(_)).recover { case NonFatal(e) => Failure(e) }
  }

  implicit class IterableOnceExtension[A, CC[X] <: IterableOnce[X]](val in: CC[A]) extends AnyVal {
    def mapSync[B](f: A => Future[B])(implicit ec: ExecutionContext, bf: BuildFrom[CC[A], B, CC[B]]): Future[CC[B]] =
      in.iterator
        .foldLeft(Future.successful(bf.newBuilder(in))) { (builderF, a) =>
          for {
            builder <- builderF
            b <- f(a)
          } yield builder.addOne(b)
        }
        .map(_.result())
  }
}
