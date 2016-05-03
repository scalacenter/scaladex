import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.AutoImport._

object Helper {
  def packageScalaJs(client: Project) = Seq(
    watchSources ++= (watchSources in client).value,

    // we want fastOpt when developing and fullOpt when publishing
    resourceGenerators in Compile += Def.task {
      val jsdeps = (packageJSDependencies in (client, Compile)).value
      val (js, map) = andSourceMap((fastOptJS in (client, Compile)).value.data)
      IO.copy(Seq(
        js -> target.value / js.getName,
        map -> target.value / map.getName,
        jsdeps -> target.value / jsdeps.getName
      )).toSeq
    }.taskValue,
    mappings in (Compile, packageBin) := (mappings in (Compile,packageBin)).value.filterNot{ case (f, r) =>
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

  private def andSourceMap(aFile: java.io.File) = (
    aFile,
    file(aFile.getAbsolutePath + ".map")
  )
}