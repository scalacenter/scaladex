package ch.epfl.scala.index
package data
package project

import cleanup._
import model._
import bintray._

import me.tongfei.progressbar._

object ProjectConvert {
  def apply(pomsAndMeta: List[(maven.MavenModel, List[BintraySearch])]): List[Project] = {
    val githubRepoExtractor = new GithubRepoExtractor
    
    val progressMeta = new ProgressBar("collecting metadata", pomsAndMeta.size)
    progressMeta.start()

    val pomsAndMetaClean = pomsAndMeta
      .map{ case (pom, metas) =>
        progressMeta.step()
        for {
          (artifactName, targets) <- ArtifactNameParser(pom.artifactId)
          version <- SemanticVersionParser(pom.version)
          github <- githubRepoExtractor(pom).headOption
        } yield (github, artifactName, targets, pom, metas, version)
      }.flatten

    
    progressMeta.stop()

    println("Convert POMs to Project")
    val licenseCleanup = new LicenseCleanup

    def pomToMavenReference(pom: maven.MavenModel) = MavenReference(pom.groupId, pom.artifactId, pom.version)

    val projects = pomsAndMetaClean
      .groupBy{ case (githubRepo, _, _, _, _, _) => githubRepo}
      .map{ case (GithubRepo(organization, repository), vs) =>
        
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

        Project(Project.Reference(organization, repository), artifacts)
      }.toList

    println("Dependencies & Reverse Dependencies")

    val releases =
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

    def upsert[K, V](map: Map[K, Set[V]], k: K, v: V) = {
      map.get(k) match {
        case Some(vs) => map.updated(k, vs + v)
        case None     => map.updated(k, Set(v))
      }
    }

    def zip[A, B](a: Option[A], b: Option[B]) = a.zip(b).headOption
    
    def link(reverse: Boolean) = {
      poms.foldLeft(Map[Release.Reference, Set[Release.Reference]]()){ case (cache, pom) =>
        pom.dependencies.foldLeft(cache){ case (cache0, dependency) =>
          zip(
            mavenReferenceToReleaseReference.get(dependencyToMaven(dependency)), 
            mavenReferenceToReleaseReference.get(pomToMavenReference(pom))
           ) match {
            case Some((depRef, pomRef)) => {
              val (source, target) =
                if(reverse) (depRef, pomRef)
                else (pomRef, depRef)

              upsert(cache0, source, target)
            }
            case None => cache0
          }
        }
      }
    }

    val dependenciesCache = link(reverse = false)
    val reverseDependenciesCache =  link(reverse = true)

    def findDependencies(release: Release): Set[Release.Reference] =
      dependenciesCache.get(release.reference).getOrElse(Set())

    def findReverseDependencies(release: Release): Set[Release.Reference] =
      reverseDependenciesCache.get(release.reference).getOrElse(Set())

    projects.map(project =>
      project.copy(artifacts = project.artifacts.map(artifact =>
        artifact.copy(releases = artifact.releases.map(release =>
          release.copy(
            dependencies = findDependencies(release),
            reverseDependencies = findReverseDependencies(release)
          )
        ))
      ))
    )
  } 
}