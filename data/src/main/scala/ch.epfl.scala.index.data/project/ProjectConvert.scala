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
    val projects = pomsAndMetaClean
      .groupBy{ case (githubRepo, _, _, _, _, _) => githubRepo}
      .map{ case (GithubRepo(organization, repository), vs) =>
        
        val artifacts = 
          vs.groupBy{ case (_, artifactName, _, _, _, _) => artifactName}.map{ case (artifactName, rs) =>
            
            val releases =
              rs.map{ case (_, _, targets, pom, metas, version) =>
                Release(
                  MavenReference(pom.groupId, pom.artifactId, pom.version),
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

    // TODO DEPS & Reverse DEPS
    // println("reverse deps")
    // val reverseCache = artifacts.foldLeft(Map[ArtifactRef, Set[ArtifactRef]]()){ case (reverse, artifact) =>
    //   artifact.dependencies.foldLeft(reverse){ case (acc, d) =>
    //     acc.get(d) match {
    //       case Some(ds) => acc.updated(d, ds + artifact.ref)
    //       case None     => acc.updated(d, Set(artifact.ref))
    //     }
    //   }
    // }

    projects
  }
  
}