// package ch.epfl.scala.index
// package elastic

// import com.sksamuel.elastic4s._, ElasticClient, ElasticDsl._

// import akka.actor.ActorSystem
// import akka.stream.ActorMaterializer

// import scala.concurrent.duration._
// import scala.concurrent.Await
// import scala.util.Success

// class SeedElasticSearch(implicit system: ActorSystem, materializer: ActorMaterializer) {
//   import system.dispatcher

//   private val poms = maven.Poms.get.collect{ case Success(p) => maven.PomConvert(p) }
//   private val scmCleanup = new ScmCleanup
//   private val licenseCleanup = new LicenseCleanup
//   private val artifacts = poms.map{p =>
//     import p._
//     Artifact(
//       ArtifactRef(
//         groupId,
//         artifactId,
//         version
//       ),
//       dependencies.map{ d =>
//         import d._
//         ArtifactRef(
//           groupId,
//           artifactId,
//           version
//         )
//       },
//       scmCleanup.find(p),
//       licenseCleanup.find(licenses)
//     )
//   }

//   def run(): Unit = {

//   }
// }