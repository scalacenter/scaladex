package ch.epfl.scala.index
package data
package project

import cleanup._
import model._
import model.misc._
import model.release._
import maven.{ReleaseModel, SbtPluginTarget}
import bintray._
import ch.epfl.scala.index.data.LocalRepository.BintraySbtPlugins
import ch.epfl.scala.index.data.project.ProjectConvert.ProjectSeed
import github._
import elastic.SaveLiveData
import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.slf4j.LoggerFactory

class ProjectConvert(paths: DataPaths) extends BintrayProtocol {

  private val format = ISODateTimeFormat.dateTime.withOffsetParsed

  private val nonStandardLibs = NonStandardLib.load(paths)

  private val log = LoggerFactory.getLogger(getClass)

  /** artifactId is often use to express binary compatibility with a scala version (ScalaTarget)
    * if the developer follow this convention we extract the relevant parts and we mark
    * the library as standard. Otherwise we either have a library like gatling or the scala library itself
    *
    * @return The artifact name (without suffix), the Scala target, whether this project is a usual Scala library or not
    */
  private def extractArtifactNameAndTarget(pom: ReleaseModel)
    : Option[(String, Option[ScalaTarget], ArtifactKind)] = {
    nonStandardLibs
      .find(
        lib =>
          lib.groupId == pom.groupId &&
            lib.artifactId == pom.artifactId)
      .map(_.lookup) match {

      case Some(ScalaTargetFromPom) =>
        pom.dependencies
          .find(dep =>
            dep.groupId == "org.scala-lang" &&
              dep.artifactId == "scala-library")
          .flatMap(dep => SemanticVersion(dep.version))
          .map(
            version =>
              // we assume binary compatibility
              (pom.artifactId,
               Some(ScalaTarget.scala(version.copy(patch = None))),
               ArtifactKind.UnconventionalScalaLib))

      case Some(NoScalaTargetPureJavaDependency) =>
        Some((pom.artifactId, None, ArtifactKind.ConventionalScalaLib))

      case Some(ScalaTargetFromVersion) =>
        SemanticVersion(pom.version).map(
          version =>
            (pom.artifactId,
             Some(ScalaTarget.scala(version)),
             ArtifactKind.UnconventionalScalaLib))

      case None =>
        // This is a usual Scala library (whose artifact name is suffixed by the Scala binary version)
        // Or it can be an sbt-plugin published as a maven style. In such a case the Scala target
        // is not suffixed to the artifact name but can be found in the model’s `sbtPluginTarget` member.
        // TODO Store the sbt target too
        pom.sbtPluginTarget
          .flatMap {
            case SbtPluginTarget(scalaVersion, _) =>
              SemanticVersion(scalaVersion)
                .map(ScalaTarget.scala)
                .orElse {
                  log.error("Unable to decode the Scala target")
                  None
                }
                .map { target =>
                  (pom.artifactId, Some(target), ArtifactKind.SbtPlugin)
                }
          }
          .orElse {
            Artifact(pom.artifactId).map {
              case (artifactName, target) =>
                (artifactName, Some(target), ArtifactKind.ConventionalScalaLib)
            }
          }
    }
  }

  private def extractMeta(
      pomsRepoSha: List[(ReleaseModel, LocalRepository, String)]) = {
    import LocalPomRepository._

    val bintray = BintrayMeta.load(paths).groupBy(_.sha1)
    val users = Meta.load(paths, UserProvided).groupBy(_.sha1)
    val central = Meta.load(paths, MavenCentral).groupBy(_.sha1)
    val sbtPluginsCreated =
      SbtPluginsData(paths)
        .read()
        ._1
        .groupBy(_.sha1)
        .mapValues(_.head.created) // Note: This is inefficient because we already loaded the data earlier and discarded the creation time

    val packagingOfInterest = Set("aar", "jar", "bundle")

    val typesafeNonOSS = Set(
      "for-subscribers-only",
      "instrumented-reactive-platform",
      "subscribers-early-access",
      "maven-releases" // too much noise
    )

    def created(metas: List[DateTime]): Option[String] =
      metas.sorted.headOption.map(format.print)

    pomsRepoSha
      .map {
        case (pom, repo, sha1) => {
          val default =
            (pom, /* created */ None, /* resolver */ None, /* keep */ true)
          repo match {
            case Bintray => {
              bintray.get(sha1) match {
                case Some(metas) => {
                  val resolver: Option[Resolver] =
                    if (metas.forall(_.isJCenter)) Option(JCenter)
                    else
                      metas.headOption.map(meta =>
                        BintrayResolver(meta.owner, meta.repo))

                  (
                    pom,
                    // create
                    created(metas.map(_.created)),
                    // resolver
                    resolver,
                    // filter
                    !metas.exists(meta =>
                      meta.owner == "typesafe" && typesafeNonOSS.contains(
                        meta.repo))
                  )
                }
                case None => {
                  log.info("no meta for pom: " + sha1)
                  default
                }
              }
            }
            case MavenCentral => {
              central.get(sha1) match {
                case Some(metas) => {
                  (
                    pom,
                    created(metas.map(_.created)),
                    None,
                    true
                  )
                }
                case None => {
                  log.info("no meta for pom: " + sha1)
                  default
                }
              }
            }
            case UserProvided =>
              users.get(sha1) match {
                case Some(metas) => {
                  (
                    pom,
                    created(metas.map(_.created)),
                    Some(UserPublished),
                    true
                  )
                }
                case None => {
                  log.info("no meta for pom: " + sha1)
                  default
                }
              }
            case BintraySbtPlugins =>
              (
                pom,
                sbtPluginsCreated.get(sha1),
                None,
                true
              )
          }
        }
      }
      .filter {
        case (pom, _, _, keep) =>
          packagingOfInterest.contains(pom.packaging) && keep
      }
      .map {
        case (pom, created, resolver, _) => (pom, created, resolver)
      }
  }

  /**
    * @param pomsRepoSha poms and associated meta information reference
    * @param stored read user update projects from disk
    * @param cachedReleases use previous released cached to workarround elasticsearch consistency (write, read)
    */
  def apply(pomsRepoSha: List[(ReleaseModel, LocalRepository, String)],
            stored: Boolean = true,
            cachedReleases: Map[Project.Reference, Set[Release]] = Map())
    : List[(Project, Set[Release])] = {

    val githubRepoExtractor = new GithubRepoExtractor(paths)

    log.info("Collecting Metadata")

    val pomsAndMetaClean = extractMeta(pomsRepoSha).flatMap {
      case (pom, created, resolver) =>
        for {
          (artifactName, target, nonStandardLib) <- extractArtifactNameAndTarget(
            pom)
          version <- SemanticVersion(pom.version)
          github <- githubRepoExtractor(pom)
        } yield
          (github,
           artifactName,
           target,
           pom,
           created,
           resolver,
           version,
           nonStandardLib)
    }

    log.info("Convert POMs to Project")
    val licenseCleanup = new LicenseCleanup(paths)

    def pomToMavenReference(pom: maven.ReleaseModel) =
      MavenReference(pom.groupId, pom.artifactId, pom.version)

    def maxMinRelease(
        releases: Set[Release]): (Option[String], Option[String]) = {
      def sortDate(rawDates: List[String]): List[String] = {
        rawDates
          .map(format.parseDateTime)
          .sorted(Descending[DateTime])
          .map(format.print)
      }

      val dates = for {
        release <- releases.toList
        date <- release.released.toList
      } yield date

      val sorted = sortDate(dates)
      (sorted.headOption, sorted.lastOption)
    }

    val storedProjects = SaveLiveData.storedProjects(paths)

    val projectsAndReleases = pomsAndMetaClean
      .groupBy {
        case (githubRepo, _, _, _, _, _, _, _) => githubRepo
      }
      .map {
        case (githubRepo @ GithubRepo(organization, repository), vs) =>
          val projectReference = Project.Reference(organization, repository)

          val cachedReleasesForProject =
            cachedReleases.get(projectReference).getOrElse(Set())

          val releases = vs.map {
            case (_,
                  artifactName,
                  target,
                  pom,
                  created,
                  resolver,
                  version,
                  artifactKind) =>
              val (targetType,
                   scalaVersion,
                   scalaJsVersion,
                   scalaNativeVersion) =
                ScalaTarget.split(target)

              Release(
                maven = pomToMavenReference(pom),
                reference = Release.Reference(
                  organization,
                  repository,
                  artifactName,
                  version,
                  target
                ),
                resolver = resolver,
                name = pom.name,
                description = pom.description,
                released = created,
                licenses = licenseCleanup(pom),
                artifactKind = artifactKind,
                id = None,
                liveData = false,
                scalaDependencies = Seq(),
                javaDependencies = Seq(),
                reverseDependencies = Seq(),
                internalDependencies = Seq(),
                targetType = targetType.getOrElse("JAVA"),
                // for artifact publish with binary, full will return binary
                // ex cats_2.11 => 2.11
                scalaVersion = scalaVersion,
                scalaJsVersion = scalaJsVersion,
                scalaNativeVersion = scalaNativeVersion
              )
          }.toSet ++ cachedReleasesForProject

          val releaseCount = releases.map(_.reference.version).toSet.size

          val (max, min) = maxMinRelease(releases)

          val defaultStableVersion =
            if (stored)
              storedProjects
                .get(Project.Reference(organization, repository))
                .map(_.defaultStableVersion)
                .getOrElse(true)
            else true

          val releaseOptions = DefaultRelease(
            repository,
            ReleaseSelection.empty,
            releases,
            None,
            defaultStableVersion
          )

          val seed =
            ProjectSeed(
              organization = organization,
              repository = repository,
              github = GithubReader(paths, githubRepo),
              artifacts = releaseOptions.map(_.artifacts.sorted).getOrElse(Nil),
              releaseCount = releaseCount,
              defaultArtifact =
                releaseOptions.map(_.release.reference.artifact),
              created = min,
              updated = max
            )

          (seed, releases)
      }
      .toList

    log.info("Dependencies & Reverse Dependencies")

    val mavenReferenceToReleaseReference = projectsAndReleases
      .flatMap {
        case (_, releases) => releases
      }
      .map(release => (release.maven, release.reference))
      .toMap

    def dependencyToMaven(dependency: maven.Dependency) =
      MavenReference(dependency.groupId,
                     dependency.artifactId,
                     dependency.version)

    val poms = pomsAndMetaClean.map { case (_, _, _, pom, _, _, _, _) => pom }

    def link(reverse: Boolean) = {
      poms.foldLeft(Map[Release.Reference, Seq[Dependency]]()) {
        case (cache, pom) =>
          pom.dependencies.foldLeft(cache) {
            case (cache0, dependency) =>
              val depMavenRef = dependencyToMaven(dependency)
              val pomMavenRef = pomToMavenReference(pom)
              val depRef = mavenReferenceToReleaseReference.get(depMavenRef)
              val pomRef = mavenReferenceToReleaseReference.get(pomMavenRef)

              (depRef, pomRef) match {

                /* We have both, scala -> scala reference */
                case (Some(dependencyReference), Some(pomReference)) =>
                  val (source, target) =
                    if (reverse) (dependencyReference, pomReference)
                    else (pomReference, dependencyReference)
                  upsert(cache0,
                         source,
                         ScalaDependency(target, dependency.scope))

                /* dependency is scala reference - works now only if reverse */
                case (Some(dependencyReference), None) =>
                  if (reverse)
                    upsert(cache0,
                           dependencyReference,
                           JavaDependency(pomMavenRef, dependency.scope))
                  else cache0

                /* works only if not reverse */
                case (None, Some(pomReference)) =>
                  if (!reverse)
                    upsert(cache0,
                           pomReference,
                           JavaDependency(depMavenRef, dependency.scope))
                  else cache0

                /* java -> java: should not happen actually */
                case (None, None) =>
                  log.error(
                    s"no reference discovered for $pomMavenRef -> $depMavenRef")
                  cache0
              }
          }
      }
    }

    val dependenciesCache = link(reverse = false)
    val reverseDependenciesCache = link(reverse = true)

    def findDependencies(release: Release): Seq[Dependency] = {
      dependenciesCache.getOrElse(release.reference, Seq())
    }

    def findReverseDependencies(release: Release): Seq[Dependency] = {
      reverseDependenciesCache.getOrElse(release.reference, Seq())
    }

    def collectDependencies(
        releases: Set[Release],
        f: Release.Reference => Option[String]): Set[String] = {
      (for {
        release <- releases.toList
        dependency <- release.scalaDependencies.toList
        r <- f(dependency.reference).toList
      } yield r).toSet
    }

    def dependencies(releases: Set[Release]): Set[String] =
      collectDependencies(releases, r => Some(r.name))

    def belongsTo(reference: Project.Reference): Dependency => Boolean = {
      //A scala project can only have scala internal dependencies
      case scalaDependency: ScalaDependency =>
        reference.organization == scalaDependency.reference.organization &&
          reference.repository == scalaDependency.reference.repository
      case _ => false
    }

    projectsAndReleases.map {
      case (seed, releases) =>
        val releasesWithDependencies = releases.map { release =>
          val dependencies = findDependencies(release)
          val externalDependencies =
            dependencies.filterNot(belongsTo(seed.reference))
          val internalDependencies =
            dependencies.filter(belongsTo(seed.reference))
          release.copy(
            scalaDependencies = externalDependencies.collect {
              case sd: ScalaDependency => sd
            },
            javaDependencies = externalDependencies.collect {
              case jd: JavaDependency => jd
            },
            reverseDependencies = findReverseDependencies(release).collect {
              case sd: ScalaDependency => sd
            },
            internalDependencies = internalDependencies.collect {
              case sd: ScalaDependency => sd
            }
          )
        }

        val project =
          seed.toProject(
            targetType = releases.map(_.targetType).toSet,
            scalaVersion = releases.flatMap(_.scalaVersion).toSet,
            scalaJsVersion = releases.flatMap(_.scalaJsVersion).toSet,
            scalaNativeVersion = releases.flatMap(_.scalaNativeVersion).toSet,
            dependencies = dependencies(releasesWithDependencies),
            dependentCount = releasesWithDependencies.view
              .flatMap(_.reverseDependencies.map(_.reference))
              .size // Note: we don’t need to call `.distinct` because `releases` is a `Set`
          )

        val updatedProject =
          if (stored) {
            storedProjects
              .get(project.reference)
              .map(_.update(project))
              .getOrElse(project)
          } else project

        (updatedProject, releasesWithDependencies)
    }
  }
}

object ProjectConvert {

  /** Intermediate data structure */
  case class ProjectSeed(
      organization: String,
      repository: String,
      github: Option[GithubInfo],
      artifacts: List[String],
      defaultArtifact: Option[String],
      releaseCount: Int,
      created: Option[String],
      updated: Option[String]
  ) {

    def reference = Project.Reference(organization, repository)

    def toProject(targetType: Set[String],
                  scalaVersion: Set[String],
                  scalaJsVersion: Set[String],
                  scalaNativeVersion: Set[String],
                  dependencies: Set[String],
                  dependentCount: Int): Project =
      Project(
        organization = organization,
        repository = repository,
        github = github,
        artifacts = artifacts,
        defaultArtifact = defaultArtifact,
        releaseCount = releaseCount,
        created = created,
        updated = updated,
        targetType = targetType,
        scalaVersion = scalaVersion,
        scalaJsVersion = scalaJsVersion,
        scalaNativeVersion = scalaNativeVersion,
        dependencies = dependencies,
        dependentCount = dependentCount
      )
  }

}
