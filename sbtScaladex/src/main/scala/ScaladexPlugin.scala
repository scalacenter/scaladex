import sbt.Keys._
import sbt._

/**
 * Place holder plugin for setting up scripted.
 */
object ScaladexPlugin extends AutoPlugin {

  object autoImport {

    lazy val Scaladex = config("scaladex") extend Compile
    lazy val scaladexKeywords = settingKey[Seq[String]]("list of keywords for your package")
    lazy val scaladexDownloadReadme = settingKey[Boolean]("defines if scaladex should download the readme file")
    lazy val scaladexDownloadInfo = settingKey[Boolean]("defines if scaladex should download the project info")
    lazy val scaladexDownloadContributors = settingKey[Boolean]("defines if scaladex should download the contributors lost")

    lazy val baseScaladexSettings = Seq(
      scaladexKeywords := Seq(),
      scaladexDownloadContributors := true,
      scaladexDownloadInfo := true,
      scaladexDownloadReadme := true
    ) ++
      inConfig(Scaladex)(
        Classpaths.ivyPublishSettings ++
          Classpaths.ivyBaseSettings ++
          Seq(
            publishTo := {

              val baseUrl = "http://localhost:8080/publish?"
              val params = List(
                "readme" -> scaladexDownloadReadme.value,
                "info" -> scaladexDownloadInfo.value,
                "contributors" -> scaladexDownloadContributors.value,
                "path" -> "" // need to be at the end!
              )
              val keywords = scaladexKeywords.value.map(key => "keywords" -> key)

              val url = baseUrl + (keywords ++ params).map {
                case(k,v) => s"$k=$v"
              }.mkString("&")

              Some("Scaladex" at url)
            },
            publishArtifact in packageBin := true,
            publishArtifact in packageDoc := true,
            publishArtifact in packageSrc := true

          )) ++ Seq(
      packagedArtifacts in Scaladex := (packagedArtifacts in Compile).value.filter(_._2.getName.endsWith(".pom"))
    )
  }

  import autoImport._
  override def trigger = allRequirements
  override lazy val projectSettings = baseScaladexSettings
}