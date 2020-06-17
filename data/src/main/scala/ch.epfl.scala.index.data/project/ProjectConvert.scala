package ch.epfl.scala.index
package data
package project

import ch.epfl.scala.index.data.bintray._
import ch.epfl.scala.index.data.cleanup._
import ch.epfl.scala.index.data.elastic.SaveLiveData
import ch.epfl.scala.index.data.github._
import ch.epfl.scala.index.data.maven.ReleaseModel
import ch.epfl.scala.index.data.project.ProjectConvert.ProjectSeed
import ch.epfl.scala.index.model._
import ch.epfl.scala.index.model.misc._
import ch.epfl.scala.index.model.release._
import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

class ProjectConvert(paths: DataPaths, githubDownload: GithubDownload)
    extends BintrayProtocol {

  private val log = LoggerFactory.getLogger(getClass)

  private val artifactMetaExtractor = new ArtifactMetaExtractor(paths)

  /**
   * @param pomsRepoSha poms and associated meta information reference
   * @param cachedReleases use previous released cached to workaround elasticsearch consistency (write, read)
   */
  def convertAll(
      pomsRepoSha: Iterable[(ReleaseModel, LocalRepository, String)],
      cachedReleases: Map[Project.Reference, Set[Release]]
  ): Iterator[(Project, Seq[Release], Seq[ScalaDependency])] = {

    val githubRepoExtractor = new GithubRepoExtractor(paths)

    log.info("Collecting Metadata")
    val pomsAndMetaClean = PomMeta(pomsRepoSha, paths).flatMap {
      case PomMeta(pom, created, resolver) =>
        for {
          artifactMeta <- artifactMetaExtractor(pom)
          version <- SemanticVersion.tryParse(pom.version)
          github <- githubRepoExtractor(pom)
        } yield
          (github,
           artifactMeta.artifactName,
           artifactMeta.scalaTarget,
           pom,
           created,
           resolver,
           version,
           artifactMeta.isNonStandard)
    }

    log.info("Convert POMs to Project")
    val licenseCleanup = new LicenseCleanup(paths)

    def pomToMavenReference(pom: maven.ReleaseModel) =
      MavenReference(pom.groupId, pom.artifactId, pom.version)

    def maxMinRelease(
        releases: Seq[Release]
    ): (Option[String], Option[String]) = {
      def sortDate(rawDates: List[String]): List[String] = {
        rawDates
          .map(PomMeta.format.parseDateTime)
          .sorted(Descending[DateTime])
          .map(PomMeta.format.print)
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
            cachedReleases.getOrElse(projectReference, Set())

          val releases = vs.map {
            case (_,
                  artifactName,
                  target,
                  pom,
                  created,
                  resolver,
                  version,
                  isNonStandardLib) =>
              val (targetType,
                   scalaVersion,
                   scalaJsVersion,
                   scalaNativeVersion,
                   sbtVersion) = target match {
                case Some(ScalaJvm(languageVersion)) =>
                  (Jvm, Some(languageVersion), None, None, None)
                case Some(ScalaJs(languageVersion, jsVersion)) =>
                  (Js, Some(languageVersion), Some(jsVersion), None, None)
                case Some(ScalaNative(languageVersion, nativeVersion)) =>
                  (Native,
                   Some(languageVersion),
                   None,
                   Some(nativeVersion),
                   None)
                case Some(SbtPlugin(languageVersion, sbtVersion)) =>
                  (Sbt, Some(languageVersion), None, None, Some(sbtVersion))
                case None => (Java, None, None, None, None)
              }

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
                isNonStandardLib = isNonStandardLib,
                id = None,
                liveData = false,
                javaDependencies = Seq(),
                targetType = targetType.toString,
                scalaVersion = scalaVersion.map(_.family),
                scalaJsVersion = scalaJsVersion.map(_.toString),
                scalaNativeVersion = scalaNativeVersion.map(_.toString),
                sbtVersion = sbtVersion.map(_.toString)
              )
          } ++ cachedReleasesForProject

          val releaseCount = releases.map(_.reference.version).size

          val (max, min) = maxMinRelease(releases)

          val defaultStableVersion = storedProjects
            .get(Project.Reference(organization, repository))
            .forall(_.defaultStableVersion)

          val releaseOptions = ReleaseOptions(
            repository,
            ReleaseSelection.empty,
            releases,
            None,
            defaultStableVersion
          )

          val github = GithubReader(paths, githubRepo)
          val seed =
            ProjectSeed(
              organization = organization,
              repository = repository,
              github = github,
              artifacts = releaseOptions.map(_.artifacts.sorted).getOrElse(Nil),
              releaseCount = releaseCount,
              defaultArtifact = releaseOptions.map(_.release.reference.artifact),
              created = min,
              updated = max
            )

          (seed, releases)
      }

    log.info("Dependencies & Reverse Dependencies")

    val mavenReferenceToReleaseReference =
      projectsAndReleases.iterator
        .flatMap { case (_, releases) => releases }
        .map(release => (release.maven, release.reference))
        .toMap

    def dependencyToMaven(dependency: maven.Dependency) =
      MavenReference(dependency.groupId,
                     dependency.artifactId,
                     dependency.version)

    val poms = pomsAndMetaClean.map { case (_, _, _, pom, _, _, _, _) => pom }

    val allDependencies: Seq[Dependency] = poms.flatMap { pom =>
      val pomMavenRef = pomToMavenReference(pom)
      mavenReferenceToReleaseReference.get(pomMavenRef) match {
        case None => Seq()
        case Some(pomRef) =>
          pom.dependencies.map { dep =>
            val depMavenRef = dependencyToMaven(dep)
            mavenReferenceToReleaseReference.get(depMavenRef) match {
              case None         => JavaDependency(pomRef, depMavenRef, dep.scope)
              case Some(depRef) => ScalaDependency(pomRef, depRef, dep.scope)
            }
          }
      }
    }

    val dependenciesByProject =
      allDependencies.groupBy(d => d.dependent.projectReference)
    val dependentCountByProject = allDependencies
      .collect { case d: ScalaDependency => d }
      .groupBy(d => d.target.projectReference)
      .mapValues(_.map(_.dependent.projectReference).distinct.size)

    projectsAndReleases.iterator.map {
      case (seed, releases) =>
        val projectDependencies =
          dependenciesByProject.getOrElse(seed.reference, Seq())
        val scalaDependencies = projectDependencies.collect {
          case d: ScalaDependency => d
        }
        val javaDependenciesByRelease = projectDependencies
          .collect { case d: JavaDependency => d }
          .groupBy(d => d.dependent)

        val releasesWithDependencies = releases.map { release =>
          release.copy(
            javaDependencies =
              javaDependenciesByRelease.getOrElse(release.reference, Seq())
          )
        }

        val project =
          seed.toProject(
            targetType = releases.map(_.targetType).distinct.toList,
            scalaVersion = releases.flatMap(_.scalaVersion).distinct.toList,
            scalaJsVersion = releases.flatMap(_.scalaJsVersion).distinct.toList,
            scalaNativeVersion =
              releases.flatMap(_.scalaNativeVersion).distinct.toList,
            sbtVersion = releases.flatMap(_.sbtVersion).distinct.toList,
            dependencies = scalaDependencies.map(d => d.target.name).toSet,
            dependentCount =
              dependentCountByProject.getOrElse(seed.reference, 0)
          )

        val updatedProject = storedProjects
          .get(project.reference)
          .map(_.update(project, paths, githubDownload, fromStored = true))
          .getOrElse(project)

        (updatedProject, releasesWithDependencies, scalaDependencies)
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

    def reference: Project.Reference =
      Project.Reference(organization, repository)

    def toProject(targetType: List[String],
                  scalaVersion: List[String],
                  scalaJsVersion: List[String],
                  scalaNativeVersion: List[String],
                  sbtVersion: List[String],
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
        sbtVersion = sbtVersion,
        dependencies = dependencies,
        dependentCount = dependentCount
      )
  }

}
