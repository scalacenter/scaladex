// package ch.epfl.scala.index
// package data
// package observations

// /*
// git submodule init
// git submodule update
// export SBT_OPTS="-Xms512M -Xmx3G -Xss1M -XX:+CMSClassUnloadingEnabled"
// sbt data/console
//  */

// import ch.epfl.scala.index._
// import data._
// import maven._

// import bintray._
// import scala.util.Success

// import com.github.nscala_time.time.Imports._
// import java.nio.charset.StandardCharsets
// import java.nio.file.{Files, Paths}

// object ReleaseGap {
//   val scalaOrg = "org.scala-lang"
//   val scalaStd = "scala-library"

//       // 2.10.0 => 20-Dec-2012
//     val versions = List(
//       "2.12.0-RC2",
//       "2.12.0-RC1",
//       "2.12.0-RC1",
//       "2.12.0-RC1",
//       "2.12.0-RC1",
//       "2.12.0-M5",
//       "2.12.0-M4-9901daf",
//       "2.12.0-M4",
//       "2.12.0-M3-dc9effe",
//       "2.12.0-M3",
//       "2.12.0-M2",
//       "2.12.0-M1",
//       "2.11.8",
//       "2.11.7",
//       "2.11.6",
//       "2.11.5",
//       "2.11.4",
//       "2.11.3",
//       "2.11.2",
//       "2.11.1",
//       "2.11.0",
//       "2.11.0-RC4",
//       "2.11.0-RC3",
//       "2.11.0-RC1",
//       "2.11.0-M8",
//       "2.11.0-M7",
//       "2.11.0-M6",
//       "2.11.0-M5",
//       "2.11.0-M4",
//       "2.11.0-M3",
//       "2.11.0-M2",
//       "2.11.0-M1",
//       "2.11.2",
//       "2.11.1",
//       "2.11.0",
//       "2.11.0-RC4",
//       "2.11.0-RC3",
//       "2.11.0-RC1",
//       "2.11.0-M8",
//       "2.11.0-M7",
//       "2.11.0-M6",
//       "2.11.0-M5",
//       "2.11.0-M4",
//       "2.11.0-M3",
//       "2.11.0-M2",
//       "2.11.0-M1",
//       "2.10.6",
//       "2.10.5",
//       "2.10.4",
//       "2.10.4-RC3",
//       "2.10.4-RC2",
//       "2.10.4-RC1",
//       "2.10.3",
//       "2.10.3-RC3",
//       "2.10.3-RC2",
//       "2.10.3-RC1",
//       "2.10.2",
//       "2.10.2-RC2",
//       "2.10.2-RC1",
//       "2.10.1",
//       "2.10.1-RC3",
//       "2.10.1-RC2",
//       "2.10.1-RC1",
//       "2.10.0",
//       "2.10.0-RC5",
//       "2.10.0-RC4",
//       "2.10.0-RC3",
//       "2.10.0-RC2",
//       "2.10.0-RC1",
//       "2.10.0-M7",
//       "2.10.0-M6",
//       "2.10.4",
//       "2.10.4-RC3",
//       "2.10.4-RC2",
//       "2.10.4-RC1",
//       "2.10.3",
//       "2.10.3-RC3",
//       "2.10.3-RC2",
//       "2.10.3-RC1",
//       "2.10.2",
//       "2.10.2-RC2",
//       "2.10.2-RC1",
//       "2.10.1",
//       "2.10.1-RC3",
//       "2.10.1-RC2",
//       "2.10.1-RC1",
//       "2.10.0",
//       "2.10.0-RC5",
//       "2.10.0-RC4",
//       "2.10.0-RC3",
//       "2.10.0-RC2",
//       "2.10.0-RC1",
//       "2.10.0-M7",
//       "2.10.0-M6",
//       "2.10.0-M5",
//       "2.10.0-M4",
//       "2.10.0-M3",
//       "2.10.0-M2",
//       "2.10.0-M1"
//     ).reverse
//     // 2.11.0 => 16-Apr-2014
//   case class Date(year: Int, month: Int)

//   implicit val dateOrdering = Ordering.by { date: Date =>
//     Date.unapply(date)
//   }

//   def histogram[A, B: Ordering](in: List[A])(f: A => B): List[(B, Int)] = {
//     in.groupBy(f).toList.map { case (k, vs) => (k, vs.size) }.sorted
//   }

//   def findDep(poms: List[(MavenModel, List[BintraySearch])],
//               version: String): List[(MavenModel, List[BintraySearch])] = {
//     def dep(version: String)(d: Dependency): Boolean = {
//       d.groupId == scalaOrg &&
//       d.artifactId == scalaStd &&
//       d.version == version
//     }
//     poms.filter { case (pom, _) => pom.dependencies.exists(dep(version)) }
//   }

//   def plot(poms: List[(MavenModel, _, _)], version: String): List[(Date, Int)] =
//     histogram(findDep(poms, version)) {
//       case (_, metas) =>
//         metas.headOption.map(_.created) match {
//           // the data is not precise enought for a day (created.getDayOfMonth)
//           case Some(created) => Date(created.getYear, created.getMonthOfYear)
//           case None => Date(-1, -1)
//         }
//     }

//   def movingTotal[K](in: List[(K, List[Int])]): List[(K, List[Int])] = {
//     in.foldLeft((versions.map(_ => 0), List.empty[(K, List[Int])])) {
//         case ((movingTotals, acc), (v, counts)) =>
//           val dotproduct = movingTotals.zip(counts).map { case (x, y) => x + y }
//           (dotproduct, (v, dotproduct) :: acc)
//       }
//       ._2
//       .reverse
//   }

//   def printCsv(in: List[(Date, List[Int])]): String = {
//     val nl = System.lineSeparator
//     val header = ("date" :: versions.toList).mkString(", ")
//     in.map {
//       case (Date(year, month), versionsCount) =>
//         (s"$year/$month" :: versionsCount.map(_.toString)).mkString(", ")
//     }.mkString(header + nl, nl, "")
//   }
//   def main(args: Array[String]): Unit = {
//     val paths = DataPaths(args.toList)

//     val base = Paths.get("observations")

//     val poms = PomsReader.loadAll(paths).collect { case Success(pomAndMeta) => pomAndMeta }
//     // List[Version, List[(Date, Count)]]
//     val histograms = versions.map(version => (version, plot(poms, version)))

//     val data = {
//       histograms.flatMap {
//         case (version, histo) =>
//           histo.map {
//             case (date, count) =>
//               (date, version, count)
//           }
//       }.groupBy { case (date, _, _) => date }.toList.sortBy { case (date, _) => date }.map {
//         case (date, vs) =>
//           (date, vs.map { case (date, version, count) => (version, count) }.toMap)
//       }
//       // List[Date, List[Count]]
//       .map {
//         case (date, versionCount) =>
//           (date, versions.map(version => versionCount.get(version).getOrElse(0)))
//       }
//     }

//     val out = printCsv(movingTotal(data))

//     Files.write(
//       base.resolve(s"data.csv"),
//       out.getBytes(StandardCharsets.UTF_8)
//     )
//   }
// }

// /*
// 2.12.0-RC2           14-Oct-2016
// 2.12.0-RC1           13-Oct-2016
// 2.12.0-RC1           27-Sep-2016
// 2.12.0-RC1           16-Sep-2016
// 2.12.0-RC1           06-Sep-2016
// 2.12.0-M5            29-Jun-2016
// 2.12.0-M4-9901daf    28-Jun-2016
// 2.12.0-M4            01-Apr-2016
// 2.12.0-M3-dc9effe    18-Mar-2016
// 2.11.8               04-Mar-2016
// 2.12.0-M3            05-Oct-2015
// 2.10.6               18-Sep-2015
// 2.12.0-M2            14-Jul-2015
// 2.11.7               22-Jun-2015
// 2.12.0-M1            01-May-2015
// 2.10.5               27-Feb-2015
// 2.11.6               26-Feb-2015
// 2.11.5               07-Jan-2015
// 2.11.4               23-Oct-2014
// 2.11.3               10-Oct-2014
// 2.11.2               23-Jul-2014
// 2.11.1               20-May-2014
// 2.11.0               16-Apr-2014
// 2.11.0-RC4           04-Apr-2014
// 2.11.0-RC3           19-Mar-2014
// 2.10.4               18-Mar-2014
// 2.11.0-RC1           28-Feb-2014
// 2.10.4-RC3           11-Feb-2014
// 2.10.4-RC2           21-Jan-2014
// 2.11.0-M8            19-Jan-2014
// 2.10.4-RC1           12-Dec-2013
// 2.11.0-M7            18-Nov-2013
// 2.11.0-M6            15-Oct-2013
// 2.10.3               27-Sep-2013
// 2.10.3-RC3           23-Sep-2013
// 2.10.3-RC2           13-Sep-2013
// 2.11.0-M5            07-Sep-2013
// 2.10.3-RC1           17-Aug-2013
// 2.11.0-M4            11-Jul-2013
// 2.10.2               06-Jun-2013
// 2.10.2-RC2           30-May-2013
// 2.10.2-RC1           21-May-2013
// 2.11.0-M3            20-May-2013
// 2.11.0-M2            14-Mar-2013
// 2.10.1               13-Mar-2013
// 2.10.1-RC3           04-Mar-2013
// 2.10.1-RC2           27-Feb-2013
// 2.10.1-RC1           12-Feb-2013
// 2.11.0-M1            07-Jan-2013
// 2.10.0               20-Dec-2012
// 2.10.0-RC5           06-Dec-2012
// 2.10.0-RC4           05-Dec-2012
// 2.10.0-RC3           21-Nov-2012
// 2.10.0-RC2           05-Nov-2012
// 2.10.0-RC1           12-Oct-2012
// 2.10.0-M7            21-Aug-2012
// 2.10.0-M6            26-Jul-2012
// 2.11.2               23-Jul-2014
// 2.11.1               20-May-2014
// 2.11.0               16-Apr-2014
// 2.11.0-RC4           04-Apr-2014
// 2.11.0-RC3           19-Mar-2014
// 2.10.4               18-Mar-2014
// 2.11.0-RC1           28-Feb-2014
// 2.10.4-RC3           11-Feb-2014
// 2.10.4-RC2           21-Jan-2014
// 2.11.0-M8            19-Jan-2014
// 2.10.4-RC1           12-Dec-2013
// 2.11.0-M7            18-Nov-2013
// 2.11.0-M6            15-Oct-2013
// 2.10.3               27-Sep-2013
// 2.10.3-RC3           23-Sep-2013
// 2.10.3-RC2           13-Sep-2013
// 2.11.0-M5            07-Sep-2013
// 2.10.3-RC1           17-Aug-2013
// 2.11.0-M4            11-Jul-2013
// 2.10.2               06-Jun-2013
// 2.10.2-RC2           30-May-2013
// 2.10.2-RC1           21-May-2013
// 2.11.0-M3            20-May-2013
// 2.11.0-M2            14-Mar-2013
// 2.10.1               13-Mar-2013
// 2.10.1-RC3           04-Mar-2013
// 2.10.1-RC2           27-Feb-2013
// 2.10.1-RC1           12-Feb-2013
// 2.11.0-M1            07-Jan-2013
// 2.10.0               20-Dec-2012
// 2.10.0-RC5           06-Dec-2012
// 2.10.0-RC4           05-Dec-2012
// 2.10.0-RC3           21-Nov-2012
// 2.10.0-RC2           05-Nov-2012
// 2.10.0-RC1           12-Oct-2012
// 2.10.0-M7            21-Aug-2012
// 2.10.0-M6            26-Jul-2012
// 2.10.0-M5            12-Jul-2012
// 2.10.0-M4            12-Jun-2012
// 2.10.0-M3            29-Apr-2012
// 2.10.0-M2            20-Feb-2012
// 2.10.0-M1            19-Jan-2012

// // source: http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.scala-lang%22%20AND%20a%3A%22scala-library%22

//  */
