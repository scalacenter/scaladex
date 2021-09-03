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
import ch.epfl.scala.index.newModel.NewDependency
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import com.github.nscala_time.time.Imports._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

class ProjectConvert(paths: DataPaths, githubDownload: GithubDownload)
    extends BintrayProtocol {

  private val log = LoggerFactory.getLogger(getClass)

  private val metaExtractor = new ArtifactMetaExtractor(paths)

  /**
   * @param pomsRepoSha poms and associated meta information reference
   * @param indexedReleases use previous indexed releases to update the project consistently
   */
  def convertAll(
      pomsRepoSha: Iterable[(ReleaseModel, LocalRepository, String)],
      indexedReleases: Map[Project.Reference, Seq[Release]]
  ): (Iterator[Project], Iterator[Release], Iterator[NewDependency]) = {

    val githubRepoExtractor = new GithubRepoExtractor(paths)

    log.info("Collecting Metadata")
    val pomsAndMetaClean = PomMeta.all(pomsRepoSha, paths).flatMap {
      case PomMeta(pom, created, resolver) =>
        for {
          artifactMeta <- metaExtractor.extractMeta(pom)
          version <- SemanticVersion.tryParse(pom.version)
          github <- githubRepoExtractor(pom)
        } yield (
          github,
          artifactMeta.artifactName,
          artifactMeta.scalaTarget,
          pom,
          created,
          resolver,
          version,
          artifactMeta.isNonStandard
        )
    }

    log.info("Convert POMs to Project")
    val licenseCleanup = new LicenseCleanup(paths)

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
      .groupBy { case (githubRepo, _, _, _, _, _, _, _) =>
        githubRepo
      }
      .map { case (githubRepo @ GithubRepo(organization, repository), vs) =>
        val projectReference = Project.Reference(organization, repository)

        val oldReleases =
          indexedReleases.getOrElse(projectReference, Set())

        val newReleases = vs.map {
          case (
                _,
                artifactName,
                target,
                pom,
                created,
                resolver,
                version,
                isNonStandardLib
              ) =>
            val (
              targetType,
              scalaVersion,
              scalaJsVersion,
              scalaNativeVersion,
              sbtVersion
            ) = target match {
              case Some(ScalaJvm(languageVersion)) =>
                (Jvm, Some(languageVersion), None, None, None)
              case Some(ScalaJs(languageVersion, jsVersion)) =>
                (Js, Some(languageVersion), Some(jsVersion), None, None)
              case Some(ScalaNative(languageVersion, nativeVersion)) =>
                (Native, Some(languageVersion), None, Some(nativeVersion), None)
              case Some(SbtPlugin(languageVersion, sbtVersion)) =>
                (Sbt, Some(languageVersion), None, None, Some(sbtVersion))
              case None => (Java, None, None, None, None)
            }

            Release(
              maven = pom.mavenRef,
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
        }

        val allReleases = newReleases ++ oldReleases

        val releaseCount = allReleases.map(_.reference.version).size

        val (max, min) = maxMinRelease(allReleases)

        val defaultStableVersion = storedProjects
          .get(Project.Reference(organization, repository))
          .forall(_.defaultStableVersion)

        val releaseOptions = ReleaseOptions(
          repository,
          ReleaseSelection.empty,
          allReleases,
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

        (seed, allReleases)
      }

    log.info("Dependencies")
    val poms = pomsAndMetaClean.map { case (_, _, _, pom, _, _, _, _) => pom }

    val allDependencies: Seq[NewDependency] =
      poms.flatMap(getDependencies).distinct

    val projectAndReleases = projectsAndReleases.map {
      case (seed, releases) =>
        val releasesWithDependencies =
          releases // todo: we will use soon NewRelase which doenst contain dependencies

        val project =
          seed.toProject(
            targetType = releases.map(_.targetType).distinct.toList,
            scalaVersion = releases.flatMap(_.scalaVersion).distinct.toList,
            scalaJsVersion = releases.flatMap(_.scalaJsVersion).distinct.toList,
            scalaNativeVersion =
              releases.flatMap(_.scalaNativeVersion).distinct.toList,
            sbtVersion = releases.flatMap(_.sbtVersion).distinct.toList,
            dependencies = Set(), // todo: Remove
            dependentCount =
              0 // dependentCountByProject.getOrElse(seed.reference, 0): should be computed using the database
          )

        val updatedProject = storedProjects
          .get(project.reference)
          .map(_.update(project, paths, githubDownload, fromStored = true))
          .getOrElse(project)

        (updatedProject, releasesWithDependencies)
    }
    (projectAndReleases.keys.iterator, projectAndReleases.values.flatten.iterator, allDependencies.iterator)
  }

  private def getDependencies(pom: ReleaseModel): List[NewDependency] =
    pom.dependencies
      .map(dep =>
        NewDependency(
          pom.mavenRef,
          dep.mavenRef,
          dep.scope.getOrElse("compile")
        )
      )
      .distinct

  def convertOne(
      pom: ReleaseModel,
      localRepository: LocalRepository,
      sha1: String,
      created: DateTime,
      githubRepo: GithubRepo,
      existingProject: Option[NewProject]
  ): Option[(NewProject, NewRelease, Seq[NewDependency])] = {
    val pomMetaOpt = PomMeta.from(pom, created, localRepository, paths, sha1)
    val githubInfo = GithubReader(paths, githubRepo)
    val licenseCleanup = new LicenseCleanup(paths)

    pomMetaOpt.flatMap { case PomMeta(pom, _, resolver) =>
      for {
        artifactMeta <- metaExtractor.extractMeta(pom)
        version <- SemanticVersion.tryParse(pom.version)
        project = existingProject
          .map(_.update(githubInfo))
          .getOrElse(
            NewProject.defaultProject(
              githubRepo.organization,
              githubRepo.repository,
              githubInfo
            )
          )
        dependencies = getDependencies(pom)
        release = NewRelease(
          pom.mavenRef,
          version,
          project.organization,
          project.repository,
          ArtifactName(artifactMeta.artifactName),
          artifactMeta.scalaTarget,
          pom.description,
          Some(created),
          resolver,
          licenseCleanup(pom),
          artifactMeta.isNonStandard
        )
      } yield (project, release, dependencies)
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

    def toProject(
        targetType: List[String],
        scalaVersion: List[String],
        scalaJsVersion: List[String],
        scalaNativeVersion: List[String],
        sbtVersion: List[String],
        dependencies: Set[String],
        dependentCount: Int
    ): Project =
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
