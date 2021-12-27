import sbt._
import Keys._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object ScalaJSHelper {
  def packageScalaJS(client: Project): Seq[Setting[_]] = Seq(
    watchSources ++= (client / watchSources).value,
    // Pick fastOpt when developing and fullOpt when publishing
    Compile / resourceGenerators += Def.task {
      val (js, map) = andSourceMap((client / Compile / fastOptJS).value.data)
      IO.copy(
        Seq(
          js -> (Compile / resourceManaged).value / js.getName,
          map -> (Compile / resourceManaged).value / map.getName
        )
      ).toSeq
    }.taskValue,
    Compile / packageBin / mappings := {
      val mappingExcludingNonOptimized =
        (Compile / packageBin / mappings).value.filterNot {
          case (f, r) =>
            f.getName.endsWith("-fastopt.js") ||
              f.getName.endsWith("js.map")
        }

      val optimized = {
        val (js, map) =
          andSourceMap((client / Compile / fullOptJS).value.data)
        Seq(
          js -> js.getName,
          map -> map.getName
        )
      }

      mappingExcludingNonOptimized ++ optimized
    }
  )

  private def andSourceMap(aFile: java.io.File) =
    aFile -> file(aFile.getAbsolutePath + ".map")
}
