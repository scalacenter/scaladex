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

  case class PomMetaStep(meta: PomMeta, keep: Boolean)

  def default(releaseModel: ReleaseModel): PomMetaStep = {
    PomMetaStep(
      PomMeta(
        releaseModel = releaseModel,
        created = None,
        resolver = None
      ),
      keep = true,
    )
  }

  def apply(pomsRepoSha: Iterable[(ReleaseModel, LocalRepository, String)],
            paths: DataPaths): Seq[PomMeta] = {

    import LocalPomRepository._

    val bintray = BintrayMeta.load(paths).groupBy(_.sha1)
    val users = Meta.load(paths, UserProvided).groupBy(_.sha1)
    val central = Meta.load(paths, MavenCentral).groupBy(_.sha1)
    val sbtPluginsCreated =
      SbtPluginsData(paths.ivysData)
        .read()
        .groupBy(_.sha1)
        .mapValues(_.head.created) // Note: This is inefficient because we already loaded the data earlier and discarded the creation time

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
      .map {
        case (pom, repo, sha1) => {
          repo match {
            case Bintray => {
              bintray.get(sha1) match {
                case Some(metas) => {
                  val resolver: Option[Resolver] =
                    if (metas.forall(_.isJCenter)) Option(JCenter)
                    else
                      metas.headOption.map(
                        meta => BintrayResolver(meta.owner, meta.repo)
                      )

                  PomMetaStep(
                    PomMeta(
                      releaseModel = pom,
                      created = created(
                        metas.map(search => new DateTime(search.created))
                      ),
                      resolver = resolver
                    ),
                    keep = !metas.exists(
                      meta =>
                        meta.owner == "typesafe" && typesafeNonOSS
                          .contains(meta.repo)
                    )
                  )
                }
                case None => {
                  log.info("no meta for pom: " + sha1)
                  default(pom)
                }
              }
            }
            case MavenCentral => {
              central.get(sha1) match {
                case Some(metas) => {
                  PomMetaStep(
                    PomMeta(
                      releaseModel = pom,
                      created = created(metas.map(_.created)),
                      resolver = None
                    ),
                    keep = true
                  )
                }
                case None => {
                  log.info("no meta for pom: " + sha1)
                  default(pom)
                }
              }
            }
            case UserProvided =>
              users.get(sha1) match {
                case Some(metas) => {
                  PomMetaStep(
                    PomMeta(
                      releaseModel = pom,
                      created = created(metas.map(_.created)),
                      resolver = Some(UserPublished)
                    ),
                    keep = true
                  )
                }
                case None => {
                  log.info("no meta for pom: " + sha1)
                  default(pom)
                }
              }
            case BintraySbtPlugins =>
              PomMetaStep(
                PomMeta(
                  releaseModel = pom,
                  created = sbtPluginsCreated.get(sha1),
                  resolver = None
                ),
                keep = true
              )
          }
        }
      }
      .filter {
        case PomMetaStep(meta, keep) =>
          packagingOfInterest.contains(meta.releaseModel.packaging) && keep
      }
      .map(_.meta)
      .toSeq
  }
}
