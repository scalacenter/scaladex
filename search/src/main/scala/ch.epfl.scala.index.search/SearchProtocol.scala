package ch.epfl.scala.index.search

import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc.GithubIssue
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.index.search.mapping._

import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.searches.RichSearchHit
import jawn.support.json4s.Parser
import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{write => nwrite}

import scala.util.{Success, Try}

trait SearchProtocol {
  implicit val formats: Formats = Serialization
    .formats(
      ShortTypeHints(
        List(
          classOf[Milestone],
          classOf[ReleaseCandidate],
          classOf[OtherPreRelease],
          classOf[BintrayResolver],
          classOf[ScalaJvm],
          classOf[ScalaJs],
          classOf[ScalaNative],
          classOf[SbtPlugin],
          classOf[MajorBinary],
          classOf[MinorBinary],
          classOf[PatchBinary],
          classOf[PreReleaseBinary],
          classOf[ScalaVersion],
          classOf[DottyVersion]
        )
      )
    )
    .preservingEmptyValues

  private def tryEither[T](f: T): Either[Throwable, T] = {
    Try(f).transform(s => Success(Right(s)), f => Success(Left(f))).get
  }

  private def nread[T: Manifest](hit: Hit) =
    Parser.parseUnsafe(hit.sourceAsString).extract[T]

  // filters a project's beginnerIssues by the inner hits returned from elastic search
  // so that only the beginnerIssues that passed the nested beginnerIssues query
  // get returned
  private def checkInnerHits(hit: RichSearchHit, p: Project): Project = {
    hit.innerHits
      .get("issues")
      .collect {
        case searchHits if searchHits.totalHits > 0 => {
          p.copy(
            github = p.github.map { github =>
              github.copy(
                filteredBeginnerIssues = searchHits
                  .getHits()
                  .map { hit =>
                    nread[GithubIssue](RichSearchHit(hit))
                  }
                  .toList
              )
            }
          )
        }
      }
      .getOrElse(p)
  }

  implicit object ProjectAs extends HitReader[Project] {

    override def read(hit: Hit): Either[Throwable, Project] = {
      tryEither(
        checkInnerHits(hit.asInstanceOf[RichSearchHit],
                       nread[Project](hit).copy(id = Some(hit.id)))
      )
    }
  }

  implicit object ProjectIndexable extends Indexable[Project] {
    override def json(project: Project): String = nwrite(project)
  }

  implicit object ReleaseReader extends HitReader[ReleaseDocument] {
    override def read(hit: Hit): Either[Throwable, ReleaseDocument] =
      tryEither(nread[ReleaseDocument](hit).copy(id = Some(hit.id)))
  }

  implicit object ReleaseIndexable extends Indexable[ReleaseDocument] {
    override def json(release: ReleaseDocument): String = nwrite(release)
  }

  implicit object DependencyReader extends HitReader[DependencyDocument] {
    override def read(hit: Hit): Either[Throwable, DependencyDocument] = {
      tryEither(nread[DependencyDocument](hit).copy(id = Some(hit.id)))
    }
  }

  implicit object DependencyIndexable extends Indexable[DependencyDocument] {
    override def json(dep: DependencyDocument): String = nwrite(dep)
  }
}

object SearchProtocol extends SearchProtocol
