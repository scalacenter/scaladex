package ch.epfl.scala.index
package data
package project

import cleanup._
import model._
import model.misc._
import model.release._

import maven.MavenModel
import bintray._
import github._
import elastic.SaveLiveData

import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat

class ProjectConvert(paths: DataPaths) extends BintrayProtocol {

  private val format = ISODateTimeFormat.dateTime.withOffsetParsed


  private val nonStandardLibs = NonStandardLib.load(paths)

  /** artifactId is often use to express binary compatibility with a scala version (ScalaTarget)
    * if the developer follow this convention we extract the relevant parts and we mark
    * the library as standard. Otherwise we either have a library like gatling or the scala library itself
    */
  private def extractArtifactNameAndTarget(pom: MavenModel): Option[(String, ScalaTarget, Boolean)] = {
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
          .map(version => (pom.artifactId, ScalaTarget(version.copy(patch = None)), true))

      case Some(ScalaTargetFromVersion) =>
        SemanticVersion(pom.version).map(version => (pom.artifactId, ScalaTarget(version), true))

      case None =>
        Artifact(pom.artifactId).map {
          case (artifactName, target) => (artifactName, target, false)
        }
    }
  }

  private def extractMeta(pomsRepoSha: List[(MavenModel, LocalRepository, String)]) = {
    import LocalRepository._

    val bintray = BintrayMeta.load(paths).groupBy(_.sha1)
    val users = Meta.load(paths, UserProvided).groupBy(_.sha1)
    val central = Meta.load(paths, MavenCentral).groupBy(_.sha1)

    val packagingOfInterest = Set("aar", "jar", "bundle")

    val typesafeNonOSS = Set(
      "for-subscribers-only",
      "instrumented-reactive-platform",
      "subscribers-early-access",
      "maven-releases" // too much noise
    )

    def created(metas: List[DateTime]): Option[String] = metas.sorted.headOption.map(format.print)
      pomsRepoSha.map{ case (pom, repo, sha1) => {
        val default = (pom, None, None, true) 
        repo match {
          case Bintray => {
            bintray.get(sha1) match {
              case Some(metas) => {
                val resolver: Option[Resolver] =
                  if(metas.forall(_.isJCenter)) Option(JCenter)
                  else metas.headOption.map(meta => BintrayResolver(meta.owner, meta.repo)) 

                (
                  pom,

                  // create
                  created(metas.map(_.created)),
                  
                  // resolver
                  resolver,
                  
                  // filter
                  !metas.exists(meta => 
                    meta.owner == "typesafe" && typesafeNonOSS.contains(meta.repo)
                  )
                )
              } 
              case None => {
                println("no meta for pom: " + sha1)
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
                println("no meta for pom: " + sha1)
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
                println("no meta for pom: " + sha1)
                default
              }
            }
        }
      }}
      .filter{ case (pom, _, _, keep) => packagingOfInterest.contains(pom.packaging) && keep}
      .map{ case (pom, created, resolver, _) => (pom, created, resolver) }
  }

  /**
    * @param pomsRepoSha poms and associated meta information reference
    * @param stored read user update projects from disk
    * @param cachedReleases use previous released cached to workarround elasticsearch consistency (write, read)
    */
  def apply(pomsRepoSha: List[(MavenModel, LocalRepository, String)],
            stored: Boolean = true,
            cachedReleases: Map[Project.Reference, Set[Release]] = Map())
    : List[(Project, Set[Release])] = {

    val githubRepoExtractor = new GithubRepoExtractor(paths)

    println("Collecting Metadata")

    val pomsAndMetaClean = extractMeta(pomsRepoSha).flatMap {
      case (pom, created, resolver) =>
        for {
          (artifactName, targets, nonStandardLib) <- extractArtifactNameAndTarget(pom)
          version <- SemanticVersion(pom.version)
          github <- githubRepoExtractor(pom)
        } yield (github, artifactName, targets, pom, created, resolver, version, nonStandardLib)
    }

    println("Convert POMs to Project")
    val licenseCleanup = new LicenseCleanup(paths)

    def pomToMavenReference(pom: maven.MavenModel) =
      MavenReference(pom.groupId, pom.artifactId, pom.version)

    def maxMinRelease(releases: Set[Release]): (Option[String], Option[String]) = {
      def sortDate(rawDates: List[String]): List[String] = {
        rawDates.map(format.parseDateTime).sorted(Descending[DateTime]).map(format.print)
      }

      val dates = for {
        release <- releases.toList
        date <- release.released.toList
      } yield date

      val sorted = sortDate(dates)
      (sorted.headOption, sorted.lastOption)
    }

    val storedProjects = SaveLiveData.storedProjects(paths)

    val projectsAndReleases = pomsAndMetaClean.groupBy {
      case (githubRepo, _, _, _, _, _, _, _) => githubRepo
    }.map {
      case (githubRepo @ GithubRepo(organization, repository), vs) =>
        val projectReference = Project.Reference(organization, repository)

        val cachedReleasesForProject =
          cachedReleases.get(projectReference).getOrElse(Set())

        val releases = vs.map {
          case (_, artifactName, targets, pom, created, resolver, version, nonStandardLib) =>
            Release(
              maven = pomToMavenReference(pom),
              reference = Release.Reference(
                organization,
                repository,
                artifactName,
                version,
                targets
              ),
              resolver = resolver,
              name = pom.name,
              description = pom.description,
              released = created, 
              licenses = licenseCleanup(pom),
              nonStandardLib = nonStandardLib
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

        val releaseOptions = DefaultRelease(repository,
                                            ReleaseSelection(None, None, None),
                                            releases,
                                            None,
                                            defaultStableVersion)

        val project =
          Project(
            organization = organization,
            repository = repository,
            github = GithubReader(paths, githubRepo),
            artifacts = releaseOptions.map(_.artifacts.sorted).getOrElse(Nil),
            releaseCount = releaseCount,
            defaultArtifact = releaseOptions.map(_.release.reference.artifact),
            created = min,
            updated = max
          )

        val updatedProject =
          if (stored) {
            storedProjects
              .get(project.reference)
              .map(form => form.update(project, live = false))
              .getOrElse(project)
          } else project

        (updatedProject, releases)
    }.toList

    println("Dependencies & Reverse Dependencies")

    val mavenReferenceToReleaseReference = projectsAndReleases.flatMap {
      case (project, releases) => releases
    }.map(release => (release.maven, release.reference)).toMap

    def dependencyToMaven(dependency: maven.Dependency) =
      MavenReference(dependency.groupId, dependency.artifactId, dependency.version)

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
                  upsert(cache0, source, ScalaDependency(target, dependency.scope))

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
                    upsert(cache0, pomReference, JavaDependency(depMavenRef, dependency.scope))
                  else cache0

                /* java -> java: should not happen actually */
                case (None, None) =>
                  println(s"no reference discovered for $pomMavenRef -> $depMavenRef")
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

    def collectDependencies(releases: Set[Release], f: Release.Reference => String): Set[String] = {
      for {
        release <- releases
        dependency <- release.scalaDependencies
      } yield f(dependency.reference)
    }

    def dependencies(releases: Set[Release]): Set[String] =
      collectDependencies(releases, _.name)

    def targets(releases: Set[Release]): Set[String] =
      collectDependencies(releases, _.target.supportName)

    def belongsTo(project: Project): Dependency => Boolean = {
      //A scala project can only have scala internal dependencies
      case scalaDependency: ScalaDependency =>
        project.reference.organization == scalaDependency.reference.organization &&
          project.reference.repository == scalaDependency.reference.repository
      case _ => false
    }

    projectsAndReleases.map {
      case (project, releases) =>
        val releasesWithDependencies = releases.map { release =>
          val dependencies = findDependencies(release)
          val externalDependencies = dependencies.filterNot(belongsTo(project))
          val internalDependencies = dependencies.filter(belongsTo(project))
          release.copy(
            scalaDependencies = externalDependencies.collect { case sd: ScalaDependency => sd },
            javaDependencies = externalDependencies.collect { case jd: JavaDependency => jd },
            reverseDependencies = findReverseDependencies(release).collect {
              case sd: ScalaDependency => sd
            },
            internalDependencies = internalDependencies.collect { case sd: ScalaDependency => sd }
          )
        }

        (
          project.copy(
            targets = targets(releasesWithDependencies),
            dependencies = dependencies(releasesWithDependencies)
          ),
          releasesWithDependencies
        )
    }
  }
}
