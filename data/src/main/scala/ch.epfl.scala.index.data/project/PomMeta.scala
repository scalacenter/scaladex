package ch.epfl.scala.index
package data
package project

import ch.epfl.scala.index.data.bintray._
import ch.epfl.scala.index.data.maven.ReleaseModel
import ch.epfl.scala.index.model.release._
import ch.epfl.scala.services.storage.DataPaths
import ch.epfl.scala.services.storage.LocalPomRepository.Bintray
import ch.epfl.scala.services.storage.LocalPomRepository.MavenCentral
import ch.epfl.scala.services.storage.LocalPomRepository.UserProvided
import ch.epfl.scala.services.storage.LocalRepository
import ch.epfl.scala.services.storage.LocalRepository.BintraySbtPlugins
import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.slf4j.LoggerFactory

case class PomMeta(
    releaseModel: ReleaseModel,
    created: Option[String],
    resolver: Option[Resolver]
)

object PomMeta {
  val format = ISODateTimeFormat.dateTime.withOffsetParsed
  private val log = LoggerFactory.getLogger(getClass)

  private def default(releaseModel: ReleaseModel): PomMeta = {
    PomMeta(
      releaseModel = releaseModel,
      created = None,
      resolver = None
    )
  }

  def all(
      pomsRepoSha: Iterable[(ReleaseModel, LocalRepository, String)],
      paths: DataPaths
  ): Seq[PomMeta] = {

    import ch.epfl.scala.services.storage.LocalPomRepository._

    val bintray = BintrayMeta.load(paths).groupBy(_.sha1)
    val users = Meta.load(paths, UserProvided).groupBy(_.sha1)
    val central = Meta.load(paths, MavenCentral).groupBy(_.sha1)
    val sbtPluginsCreated =
      SbtPluginsData(paths.ivysData)
        .read()
        .groupBy(_.sha1)
        .view
        .mapValues(
          _.head.created
        ) // Note: This is inefficient because we already loaded the data earlier and discarded the creation time

    def created(metas: List[DateTime]): Option[String] =
      metas.sorted.headOption.map(format.print)

    pomsRepoSha.iterator
      .filter { case (pom, _, _) => pom.isPackagingOfInterest }
      .flatMap {
        case (pom, repo, sha1) => {
          repo match {
            case Bintray => {
              bintray.get(sha1) match {
                case Some(metas) =>
                  if (metas.exists(_.isTypesafeNonOSS)) None
                  else {
                    val resolver: Option[Resolver] =
                      if (metas.forall(_.isJCenter)) Option(JCenter)
                      else
                        metas.headOption.map(meta =>
                          BintrayResolver(meta.owner, meta.repo)
                        )

                    Some(
                      PomMeta(
                        releaseModel = pom,
                        created = created(
                          metas.map(search => new DateTime(search.created))
                        ),
                        resolver = resolver
                      )
                    )
                  }
                case None => {
                  log.info("no meta for pom: " + sha1)
                  Some(default(pom))
                }
              }
            }
            case MavenCentral => {
              central
                .get(sha1)
                .map { metas =>
                  PomMeta(
                    releaseModel = pom,
                    created = created(metas.map(_.created)),
                    resolver = None
                  )
                }
                .orElse(Some(default(pom)))
            }
            case UserProvided =>
              users
                .get(sha1)
                .map { metas =>
                  PomMeta(
                    releaseModel = pom,
                    created = created(metas.map(_.created)),
                    resolver = None
                  )
                }
                .orElse(Some(default(pom)))
            case BintraySbtPlugins =>
              Some(
                PomMeta(
                  releaseModel = pom,
                  created = sbtPluginsCreated.get(sha1),
                  resolver = None
                )
              )
          }
        }
      }
      .toSeq
  }

  def from(
      pom: ReleaseModel,
      created: DateTime,
      localRepository: LocalRepository,
      paths: DataPaths,
      sha1: String
  ): Option[PomMeta] = {
    val createdString = PomMeta.format.print(created) // todo: keep Datetime

    if (!pom.isPackagingOfInterest) None
    else {
      // the only case where resolver has a value is when it's Bintray.
      // the only case where we don't return a PomMeta, is when it's a typesafe non OSS
      // this code maybe not needed anymore since bintray don't store anymore projects.
      localRepository match {
        case Bintray =>
          val bintray = BintrayMeta.load(paths).groupBy(_.sha1)
          bintray.get(sha1) match {
            case Some(metas) =>
              if (metas.exists(_.isTypesafeNonOSS)) None
              else {
                val resolver =
                  if (metas.forall(_.isJCenter)) Option(JCenter)
                  else
                    metas.headOption.map(meta =>
                      BintrayResolver(meta.owner, meta.repo)
                    )
                Some(PomMeta(pom, Some(createdString), resolver))
              }
            case None =>
              log.info("no meta for pom: " + sha1)
              Some(PomMeta(pom, Some(createdString), None))
          }
        case MavenCentral => Some(PomMeta(pom, Some(createdString), None))
        case UserProvided => Some(PomMeta(pom, Some(createdString), None))
        case BintraySbtPlugins => Some(PomMeta(pom, Some(createdString), None))
      }

    }
  }

}
