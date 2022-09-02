package scaladex.view

import play.twirl.api.Html
import play.twirl.api.StringInterpolation
import scaladex.core.model.Artifact

case class InstallTab(ref: String, title: String, install: String, description: Html)

object InstallTab {
  def allOf(artifact: Artifact, cliArtifacts: Set[Artifact.Name]): Seq[InstallTab] = {
    val coursierTab =
      if (cliArtifacts.contains(artifact.artifactName))
        artifact.csLaunch.map(
          InstallTab(
            "coursier",
            "Coursier",
            _,
            html"""<a href="https://get-coursier.io/docs/cli-overview">Coursier</a>"""
          )
        )
      else None
    val sbtTab = artifact.sbtInstall.map(install => InstallTab("sbt", "sbt", install, html""))
    val millTab = artifact.millInstall.map(
      InstallTab(
        "mill",
        "Mill",
        _,
        html"""<a href="https://com-lihaoyi.github.io/mill">Mill build tool</a>"""
      )
    )
    val scalaCliTab = artifact.scalaCliInstall.map(
      InstallTab(
        "scala-cli",
        "Scala CLI",
        _,
        html"""<a href="https://scala-cli.virtuslab.org/docs/overview">Scala CLI</a>"""
      )
    )
    val ammoniteTab = artifact.ammInstall.map(
      InstallTab(
        "ammonite",
        "Ammonite",
        _,
        html"""<a href="https://ammonite.io/#Ammonite-REPL">Ammonite REPL</a>"""
      )
    )
    val mavenTab = artifact.mavenInstall.map(InstallTab("maven", "Maven", _, html""))
    val gradleTab = artifact.gradleInstall.map(InstallTab("gradle", "Gradle", _, html""))

    coursierTab.toSeq ++ sbtTab ++ millTab ++ scalaCliTab ++ ammoniteTab ++ mavenTab ++ gradleTab
  }
}
