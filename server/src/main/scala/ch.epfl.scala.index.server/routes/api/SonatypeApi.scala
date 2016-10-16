// package ch.epfl.scala.index
// package server
// package routes
// package api

// case class MavenReference(
//   groupId: String,
//   artifactId: String,
//   version: String
// )

// case class SonatypeModel(
//   date: String, // ISO 8601
//   reference: MavenReference,
//   scmInfo: Option[String], // scm:git:git@github.com:scalacenter/scaladex.git
//   dependencies: List[Dependency]
// )

// case class Dependency(
//   reference: MavenReference,
//   scope: Option[String] // test, runtime, etc
// )

// class SonatypeApi extends Json4sSupport {
//   val routes =
//     put {
//       path("api" / "sonatype") {
//         entity(as[List[SonatypeModel]]) { poms =>
//           complete(SonatypeProcess(poms))
//         }
//       }
//     }
// }