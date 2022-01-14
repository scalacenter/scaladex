package scaladex.data.meta

import java.time.Instant
import java.time.OffsetDateTime

import com.typesafe.scalalogging.LazyLogging
import scaladex.core.model.BintrayResolver
import scaladex.core.model.JCenter
import scaladex.core.model.Resolver
import scaladex.core.model.data.LocalPomRepository._
import scaladex.core.model.data.LocalRepository
import scaladex.core.model.data.LocalRepository._
import scaladex.data.Meta
import scaladex.data.bintray.BintrayMeta
import scaladex.data.bintray.SbtPluginsData
import scaladex.data.maven.ArtifactModel
import scaladex.infra.storage.DataPaths

class PomMetaExtractor(
    paths: DataPaths
) extends LazyLogging {
  import PomMetaExtractor._

  private val bintray = BintrayMeta.load(paths).groupBy(_.sha1)
  private val users = Meta.load(paths, UserProvided).groupBy(_.sha1)
  private val central = Meta.load(paths, MavenCentral).groupBy(_.sha1)
  private val sbtPluginCreationDates =
    SbtPluginsData(paths.ivysData)
      .read()
      .groupBy(_.sha1)
      .view
      .mapValues(p => parseCreationDate(p.head.created))
      .toMap // Note: This is inefficient because we already loaded the data earlier and discarded the creation time

  def extract(
      pom: ArtifactModel,
      creationDate: Option[Instant],
      localRepository: LocalRepository,
      sha1: String
  ): Option[PomMeta] =
    if (!pom.isPackagingOfInterest) None
    else {
      // the only case where resolver has a value is when it's Bintray.
      // the only case where we don't return a PomMeta, is when it's a typesafe non OSS
      // this code maybe not needed anymore since bintray don't store anymore projects.
      localRepository match {
        case Bintray =>
          bintray.get(sha1) match {
            case Some(metas) =>
              if (metas.exists(_.isTypesafeNonOSS)) None
              else {
                val resolver =
                  if (metas.forall(_.isJCenter)) Option(JCenter)
                  else
                    metas.headOption.map(meta => BintrayResolver(meta.owner, meta.repo))
                val storedCreationDate = getCreationDate(metas.map(_.created))
                Some(PomMeta(pom, creationDate.orElse(storedCreationDate), resolver))
              }
            case None =>
              logger.info("no meta for pom: " + sha1)
              Some(PomMeta(pom, creationDate, None))
          }
        case MavenCentral =>
          val storedCreationDate = central.get(sha1).map(_.map(_.created)).flatMap(getCreationDate)
          Some(PomMeta(pom, creationDate.orElse(storedCreationDate), None))
        case UserProvided =>
          val storedCreationDate = users.get(sha1).map(_.map(_.created)).flatMap(getCreationDate)
          Some(PomMeta(pom, creationDate.orElse(storedCreationDate), None))
        case BintraySbtPlugins =>
          val storedCreationDate = sbtPluginCreationDates.get(sha1)
          Some(PomMeta(pom, creationDate.orElse(storedCreationDate), None))
      }
    }

  def all(pomsRepoSha: Iterable[(ArtifactModel, LocalRepository, String)]): Seq[PomMeta] =
    pomsRepoSha.iterator
      .filter { case (pom, _, _) => pom.isPackagingOfInterest }
      .flatMap {
        case (pom, repo, sha1) =>
          repo match {
            case Bintray =>
              bintray.get(sha1) match {
                case Some(metas) =>
                  if (metas.exists(_.isTypesafeNonOSS)) None
                  else {
                    val resolver: Option[Resolver] =
                      if (metas.forall(_.isJCenter)) Option(JCenter)
                      else metas.headOption.map(meta => BintrayResolver(meta.owner, meta.repo))

                    Some(
                      PomMeta(
                        artifactModel = pom,
                        creationDate = getCreationDate(metas.map(_.created)),
                        resolver = resolver
                      )
                    )
                  }
                case None =>
                  logger.info("no meta for pom: " + sha1)
                  Some(PomMeta.default(pom))
              }
            case MavenCentral =>
              central
                .get(sha1)
                .map { metas =>
                  PomMeta(
                    artifactModel = pom,
                    creationDate = getCreationDate(metas.map(_.created)),
                    resolver = None
                  )
                }
                .orElse(Some(PomMeta.default(pom)))
            case UserProvided =>
              users
                .get(sha1)
                .map { metas =>
                  PomMeta(
                    artifactModel = pom,
                    creationDate = getCreationDate(metas.map(_.created)),
                    resolver = None
                  )
                }
                .orElse(Some(PomMeta.default(pom)))
            case BintraySbtPlugins =>
              Some(
                PomMeta(
                  artifactModel = pom,
                  creationDate = sbtPluginCreationDates.get(sha1),
                  resolver = None
                )
              )
          }
      }
      .toSeq
}

object PomMetaExtractor {
  def parseCreationDate(date: String): Instant =
    OffsetDateTime.parse(date).toInstant

  private def getCreationDate(dates: Seq[String]): Option[Instant] =
    dates.map(PomMetaExtractor.parseCreationDate).minOption
}
