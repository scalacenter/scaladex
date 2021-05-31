package ch.epfl.scala.index
package data
package project

import maven.ReleaseModel
import model.release._
import data.bintray._
import data.LocalRepository._

import org.joda.time.DateTime
import com.github.nscala_time.time.Imports._
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

    import LocalPomRepository._

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

    val packagingOfInterest = Set("aar", "jar", "bundle", "pom")

    val typesafeNonOSS = Set(
      "for-subscribers-only",
      "instrumented-reactive-platform",
      "subscribers-early-access",
      "maven-releases" // too much noise
    )

    def created(metas: List[DateTime]): Option[String] =
      metas.sorted.headOption.map(format.print)

    pomsRepoSha.iterator
      .filter { case (pom, _, _) =>
        packagingOfInterest.contains(pom.packaging)
      }
      .flatMap {
        case (pom, repo, sha1) => {
          repo match {
            case Bintray => {
              bintray.get(sha1) match {
                case Some(metas) =>
                  def isTypesafeNonOSS(meta: BintraySearch): Boolean = {
                    meta.owner == "typesafe" &&
                    typesafeNonOSS.contains(meta.repo)
                  }

                  if (metas.exists(isTypesafeNonOSS)) None
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
}
