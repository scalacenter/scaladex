package scaladex.core.util

import scala.collection.BuildFrom
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

object ScalaExtensions:
  extension [A, CC[X] <: IterableOnce[X], To](in: CC[Future[A]])
    def sequence(using BuildFrom[CC[Future[A]], A, To], ExecutionContext): Future[To] =
      Future.sequence(in)

  extension [A](in: Try[A])
    def toFuture: Future[A] = in match
      case Failure(exception) => Future.failed(exception)
      case Success(value) => Future.successful(value)

  extension [A](in: Option[A])
    def toFuture: Future[A] = in match
      case None => Future.failed(new NoSuchElementException("None.get"))
      case Some(value) => Future.successful(value)

  extension [A](in: Future[A])
    def mapFailure(f: Throwable => Throwable)(using ExecutionContext): Future[A] =
      in.recoverWith { case NonFatal(e) => Future.failed(f(e)) }

    def failWithTry(using ExecutionContext): Future[Try[A]] =
      in.map(Success(_)).recover { case NonFatal(e) => Failure(e) }

  /** Warning: it is not lazy on lazy collections
    */
  extension [A, CC[X] <: IterableOnce[X]](in: CC[A])
    def mapSync[B](f: A => Future[B])(using bf: BuildFrom[CC[A], B, CC[B]])(using ExecutionContext): Future[CC[B]] =
      in.iterator
        .foldLeft(Future.successful(bf.newBuilder(in))) { (builderF, a) =>
          for
            builder <- builderF
            b <- f(a)
          yield builder.addOne(b)
        }
        .map(_.result())

    def flatMapSync[B](
        f: A => Future[IterableOnce[B]]
    )(using bf: BuildFrom[CC[A], B, CC[B]])(using ExecutionContext): Future[CC[B]] =
      in.iterator
        .foldLeft(Future.successful(bf.newBuilder(in))) { (builderF, a) =>
          for
            builder <- builderF
            bs <- f(a)
          yield builder.addAll(bs)
        }
        .map(_.result())
  end extension

  extension [A](iterator: Iterator[A])
    def foreachSync(f: A => Future[Unit])(using ExecutionContext): Future[Unit] =
      if iterator.hasNext then f(iterator.next()).flatMap(_ => iterator.foreachSync(f))
      else Future.successful(())

  extension (duration: FiniteDuration)
    def prettyPrint: String =
      def plural(n: Long, word: String): String = if n == 1 then s"1 $word" else s"$n ${word}s"

      duration match
        case duration if duration.toSeconds == 0 => plural(duration.toMillis, "millisecond")
        case duration if duration.toMinutes == 0 => plural(duration.toSeconds, "second")
        case duration if duration.toHours == 0 => plural(duration.toMinutes, "minute")
        case duration if duration.toDays == 0 => plural(duration.toHours, "hour")
        case duration if duration.toDays < 30 => plural(duration.toDays, "day")
        case duration if duration.toDays >= 30 && duration.toDays <= 365 => plural(duration.toDays / 30, "month")
        case duration if duration.toDays > 365 => plural(duration.toDays / 365, "year")
        case _ => duration.toString()
    end prettyPrint

    def shortPrint: String =
      def plural(n: Long, word: String): String = if n == 1 then s"1 $word" else s"$n ${word}s"

      duration match
        case duration if duration.toSeconds == 0 => s"${duration.toMillis}ms"
        case duration if duration.toMinutes == 0 => s"${duration.toSeconds}s"
        case duration if duration.toHours == 0 => s"${duration.toMinutes}min"
        case duration if duration.toDays == 0 => s"${duration.toHours}h"
        case duration if duration.toDays < 30 => plural(duration.toDays, "day")
        case duration if duration.toDays >= 30 && duration.toDays <= 365 => plural(duration.toDays / 30, "month")
        case duration if duration.toDays > 365 => plural(duration.toDays / 365, "year")
        case _ => duration.toString()
    end shortPrint
  end extension
end ScalaExtensions
