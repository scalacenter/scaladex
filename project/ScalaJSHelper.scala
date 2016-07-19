import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport._
import org.scalajs.sbtplugin.cross.CrossType

object Version {
  val upickle = "0.4.1"
  val scalatags = "0.6.0"
  val autowire = "0.2.5"
}

object ScalaJSHelper {

  /** Tweak JS sbt config so that sources for javascript go into `client`,
    * JVM sources into `server` * and shared sources into the `shared` folder.
    */
  object CustomCrossType extends CrossType {
    override def projectDir(crossBase: File, projectType: String): File = {
      if (projectType == "jvm") crossBase / "server"
      else if (projectType == "js") crossBase / "client"
      else throw new Error("Something wrong happened in the ScalaJS CrossType.")
    }

    override def sharedSrcDir(projectBase: File, conf: String): Option[File] =
      Some(projectBase.getParentFile / "shared" / "src" / conf / "scala")
  }

  def packageScalaJS(client: Project) = Seq(
    watchSources ++= (watchSources in client).value,

    // Pick fastOpt when developing and fullOpt when publishing
    resourceGenerators in Compile += Def.task {
      val jsdeps = (packageJSDependencies in (client, Compile)).value
      val (js, map) = andSourceMap((fastOptJS in (client, Compile)).value.data)
      IO.copy(Seq(
        js -> target.value / js.getName,
        map -> target.value / map.getName,
        jsdeps -> target.value / jsdeps.getName
      )).toSeq
    }.taskValue,
    mappings in (Compile, packageBin) := (mappings in (Compile,packageBin))
      .value.filterNot{ case (f, r) =>
        f.getName.endsWith("-fastopt.js") ||
          f.getName.endsWith("js.map")
      } ++ {
      val (js, map) = andSourceMap((fullOptJS in (client, Compile)).value.data)
      Seq(
        js -> js.getName,
        map -> map.getName
      )
    }
  )

  private def andSourceMap(aFile: java.io.File) =
    aFile -> file(aFile.getAbsolutePath + ".map")
}

