package ch.epfl.scala.index
package data
package project

import cleanup._
import model._
import model.misc._
import bintray._
import github._
import release._

import me.tongfei.progressbar._
import org.joda.time.DateTime

object ProjectConvert {

  def apply(pomsAndMeta: List[(maven.MavenModel, List[BintraySearch])]): List[Project] = {
    val githubRepoExtractor = new GithubRepoExtractor
    
    val progressMeta = new ProgressBar("collecting metadata", pomsAndMeta.size)
    progressMeta.start()

    val pomsAndMetaClean = pomsAndMeta
      .flatMap{ case (pom, metas) =>
        progressMeta.step()
        for {
          (artifactName, targets) <- ArtifactNameParser(pom.artifactId)
          version <- SemanticVersionParser(pom.version)
          github <- githubRepoExtractor(pom).headOption
        } yield (github, artifactName, targets, pom, metas, version)
      }

    
    progressMeta.stop()

    println("Convert POMs to Project")
    val licenseCleanup = new LicenseCleanup

    def pomToMavenReference(pom: maven.MavenModel) = MavenReference(pom.groupId, pom.artifactId, pom.version)

    def maxMinRelease(artifacts: List[Artifact]): (Option[String], Option[String]) = {
      import com.github.nscala_time.time.Imports._
      import org.joda.time.format.ISODateTimeFormat

      val format = ISODateTimeFormat.dateTime.withOffsetParsed

      val dates =
        for {
          artifact <- artifacts
          release  <- artifact.releases
          date     <- release.releaseDates
        } yield format.parseDateTime(date.value)

      val sorted = dates.sorted(Descending[DateTime])

      def print(date: Option[DateTime]) = date.map(format.print)

      (print(sorted.headOption), print(sorted.lastOption))
    }

    val projects = pomsAndMetaClean
      .groupBy{ case (githubRepo, _, _, _, _, _) => githubRepo}
      .map{ case (githubRepo @ GithubRepo(organization, repository), vs) =>
        
        val artifacts = 
          vs.groupBy{ case (_, artifactName, _, _, _, _) => artifactName}.map{ case (artifactName, rs) =>
            
            val releases =
              rs.map{ case (_, _, targets, pom, metas, version) =>
                Release(
                  pomToMavenReference(pom),
                  Release.Reference(
                    organization,
                    artifactName,
                    version,
                    targets
                  ),
                  pom.name,
                  pom.description,
                  metas.map(meta => ISO_8601_Date(meta.created.toString)), // +/- 3 days offset
                  metas.forall(meta => meta.owner == "bintray" && meta.repo == "jcenter"),
                  licenseCleanup(pom)
                )
              }
            Artifact(Artifact.Reference(organization, artifactName), releases)
          }.toList

        val (max, min) = maxMinRelease(artifacts)

        Project(
          Project.Reference(organization, repository),
          artifacts,
          GithubReader(githubRepo),
          Keywords(githubRepo),
          created = min,
          lastUpdate = max
        )
      }.toList

    println("Dependencies & Reverse Dependencies")

    val releases: List[Release] =
      for {
        project  <- projects
        artifact <- project.artifacts
        release  <- artifact.releases
      } yield release

    val mavenReferenceToReleaseReference = releases.map(release =>
      (release.maven, release.reference)
    ).toMap

    def dependencyToMaven(dependency: maven.Dependency) = 
      MavenReference(dependency.groupId, dependency.artifactId, dependency.version)

    val poms = pomsAndMetaClean.map{ case (_, _, _, pom, _, _) => pom }

    def link(reverse: Boolean) = {
      poms.foldLeft(Map[Release.Reference, Seq[Dependency]]()){ case (cache, pom) =>
        pom.dependencies.foldLeft(cache){ case (cache0, dependency) =>

          val depMavenRef = dependencyToMaven(dependency)
          val pomMavenRef = pomToMavenReference(pom)
          val depRef = mavenReferenceToReleaseReference.get(depMavenRef)
          val pomRef = mavenReferenceToReleaseReference.get(pomMavenRef)

          (depRef, pomRef) match {

            /* We have both, scala -> scala reference */
            case (Some(dependencyReference), Some(pomReference)) =>

              val (source, target) = if (reverse) (dependencyReference, pomReference) else (pomReference, dependencyReference)
              upsert(cache0, source, ScalaDependency(target, dependency.scope))

            /* dependency is scala reference - works now only if reverse */
            case (Some(dependencyReference), None) =>

              if (reverse) upsert(cache0, dependencyReference, JavaDependency(pomMavenRef, dependency.scope))
              else cache0

            /* works only if not reverse */
            case (None, Some(pomReference)) =>

              if (!reverse) upsert(cache0, pomReference, JavaDependency(depMavenRef, dependency.scope))
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

    // /* get the supported tags for a project artifact. supported means
    //  * the scala target like scala_2.11 or scala.js_2.11_0.6 or even
    //  * featured and very popular artifacts like spark. Featured artifacts are
    //  * done by reverse lookup
    //  *
    //  * @param artifacts the current artifact to inspect
    //  * @return
    //  */
    // def extractSupportTags(artifacts: List[Artifact]): List[String] = {

    //   artifacts.foldLeft(Seq[String]()) { (stack, artifact) =>
    //     stack ++ artifact.releases.flatMap { r =>

    //       /* scala target */
    //       val target: String = r.reference.targets.supportName
    //       val featured: Seq[String] = featuredArtifacts.filter { feat =>
    //         r.reverseDependencies.exists(_.dependency.name.startsWith(feat._1))
    //       }.values.toSeq
    //       if (stack.contains(target)) featured else featured :+ target
    //     }
    //   }.foldLeft(Seq[String]()){ (stack, dep) => if (stack.contains(dep)) stack else stack :+ dep}
    //   .toList
    // }

    def collectDependencies(artifacts: List[Artifact], f: Release.Reference => String): List[String] = {
      for {
        artifact <- artifacts
        release <- artifact.releases
        dependency <- release.scalaDependencies
      } yield f(dependency.reference)
    }

    def dependencies(artifacts: List[Artifact]): List[String] = collectDependencies(artifacts, _.name)

    def targets(artifacts: List[Artifact]): List[String] = collectDependencies(artifacts, _.target.supportName)
    

    projects.map { project =>

      val artifacts: List[Artifact] = project.artifacts.map(artifact =>
        artifact.copy(releases = artifact.releases.map { release =>
          val dependencies = findDependencies(release)
          release.copy(
            scalaDependencies = dependencies.collect { case sd: ScalaDependency => sd },
            javaDependencies = dependencies.collect { case jd: JavaDependency => jd },
            reverseDependencies = findReverseDependencies(release).collect { case sd: ScalaDependency => sd }
          )
        })
      )

      project.copy(
        artifacts = artifacts,
        targets = targets(artifacts), 
        dependencies = dependencies(artifacts)
      )
    }
  }
}