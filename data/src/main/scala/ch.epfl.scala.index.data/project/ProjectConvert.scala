package ch.epfl.scala.index
package data
package project

import cleanup._
import model._
import model.misc._
import bintray._
import maven.MavenModel
import github._
import release._

import me.tongfei.progressbar._
import org.joda.time.DateTime

object ProjectConvert extends BintrayProtocol {

  val nonStandardLibs = NonStandardLib.load()
  
  /** artifactId is often use to express binary compatibility with a scala version (ScalaTarget)
    * if the developer follow this convention we extract the relevant parts and we mark
    * the library as standard. Otherwise we either have a library like gatling or the scala library itself
    */
  def extractArtifactNameAndTarget(pom: MavenModel): Option[(String, ScalaTarget, Boolean)] = {
    nonStandardLibs.find(lib => 
      lib.groupId == pom.groupId && 
      lib.artifactId == pom.artifactId).map(_.lookup) match {

      case Some(ScalaTargetFromPom) =>
        pom.dependencies
          .find(dep =>
            dep.groupId == "org.scala-lang" &&
            dep.artifactId == "scala-library"
          )
          .flatMap(dep => SemanticVersion(dep.version))
          .map(version => (pom.artifactId, ScalaTarget(version.copy(patch = None)), true))

      case Some(ScalaTargetFromVersion) => 
        SemanticVersion(pom.version)
          .map(version => (pom.artifactId, ScalaTarget(version), true))

      case None =>
        Artifact(pom.artifactId).map{ case (artifactName, target) => (artifactName, target, false) }
    }
  }

  def apply(pomsAndMeta: List[(MavenModel, List[BintraySearch])]): List[(Project, List[Release])] = {
    val githubRepoExtractor = new GithubRepoExtractor

    val progressMeta = new ProgressBar("collecting metadata", pomsAndMeta.size)
    progressMeta.start()

    val pomsAndMetaClean = pomsAndMeta.flatMap {
      case (pom, metas) =>
        progressMeta.step()

        val repos = githubRepoExtractor(pom)
        
        for {
          (artifactName, targets, nonStandardLib) <- extractArtifactNameAndTarget(pom)
          version <- SemanticVersion(pom.version)
          github <- repos
                     .find(githubRepoExtractor.claimedRepos.contains)
                     .orElse(repos.headOption)
        } yield (github, artifactName, targets, pom, metas, version, nonStandardLib)
    }

    progressMeta.stop()

    println("Convert POMs to Project")
    val licenseCleanup = new LicenseCleanup

    def pomToMavenReference(pom: maven.MavenModel) =
      MavenReference(pom.groupId, pom.artifactId, pom.version)

    import com.github.nscala_time.time.Imports._
    import org.joda.time.format.ISODateTimeFormat
    val format = ISODateTimeFormat.dateTime.withOffsetParsed

    def maxMinRelease(
        releases: List[Release]): (Option[String], Option[String]) = {
      def sortDate(rawDates: List[String]): List[String] = {
        rawDates
          .map(format.parseDateTime)
          .sorted(Descending[DateTime])
          .map(format.print)
      }

      val dates = for {
        release <- releases
        date    <- release.released.toList
      } yield date

      val sorted = sortDate(dates)
      (sorted.headOption, sorted.lastOption)
    }

    val projectsAndReleases = pomsAndMetaClean.groupBy {
      case (githubRepo, _, _, _, _, _, _) => githubRepo
    }.map {
      case (githubRepo @ GithubRepo(organization, repository), vs) =>
        val projectReference = Project.Reference(organization, repository)

        val releases = vs.map {
          case (_, artifactName, targets, pom, metas, version, nonStandardLib) =>
            val mavenCentral = metas.forall(meta =>
                  meta.owner == "bintray" && meta.repo == "jcenter")
            val resolver =
              if (mavenCentral) None
              else
                metas
                  .map(meta => BintrayResolver(meta.owner, meta.repo))
                  .headOption

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
                released = metas
                  .map(_.created)
                  .sorted
                  .headOption
                  .map(format.print), // +/- 3 days offset
                mavenCentral = mavenCentral,
                licenses = licenseCleanup(pom),
                nonStandardLib = nonStandardLib
            )
        }

        val (max, min) = maxMinRelease(releases)

        (
            Project(
                organization,
                repository,
                GithubReader(githubRepo),
                Keywords(githubRepo),
                created = min,
                updated = max
            ),
            releases
        )

    }.toList

    println("Dependencies & Reverse Dependencies")

    val mavenReferenceToReleaseReference = projectsAndReleases.flatMap {
      case (project, releases) => releases
    }.map(release => (release.maven, release.reference)).toMap

    def dependencyToMaven(dependency: maven.Dependency) =
      MavenReference(dependency.groupId,
                     dependency.artifactId,
                     dependency.version)

    val poms = pomsAndMetaClean.map { case (_, _, _, pom, _, _, _) => pom }

    def link(reverse: Boolean) = {
      poms.foldLeft(Map[Release.Reference, Seq[Dependency]]()) {
        case (cache, pom) =>
          pom.dependencies.foldLeft(cache) {
            case (cache0, dependency) =>
              val depMavenRef = dependencyToMaven(dependency)
              val pomMavenRef = pomToMavenReference(pom)
              val depRef      = mavenReferenceToReleaseReference.get(depMavenRef)
              val pomRef      = mavenReferenceToReleaseReference.get(pomMavenRef)

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
                  println(
                      s"no reference discovered for $pomMavenRef -> $depMavenRef")
                  cache0
              }
          }
      }
    }

    val dependenciesCache        = link(reverse = false)
    val reverseDependenciesCache = link(reverse = true)

    def findDependencies(release: Release): Seq[Dependency] = {
      dependenciesCache.getOrElse(release.reference, Seq())
    }

    def findReverseDependencies(release: Release): Seq[Dependency] = {
      reverseDependenciesCache.getOrElse(release.reference, Seq())
    }

    def collectDependencies(releases: List[Release],
                            f: Release.Reference => String): List[String] = {
      for {
        release    <- releases
        dependency <- release.scalaDependencies
      } yield f(dependency.reference)
    }

    def dependencies(releases: List[Release]): List[String] =
      collectDependencies(releases, _.name).distinct

    def targets(releases: List[Release]): List[String] =
      collectDependencies(releases, _.target.supportName).distinct

    projectsAndReleases.map {
      case (project, releases) =>
        val releasesWithDependencies = releases.map { release =>
          val dependencies = findDependencies(release)
          release.copy(
              scalaDependencies = dependencies.collect {
                case sd: ScalaDependency => sd
              },
              javaDependencies = dependencies.collect {
                case jd: JavaDependency => jd
              },
              reverseDependencies = findReverseDependencies(release).collect {
                case sd: ScalaDependency => sd
              }
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
