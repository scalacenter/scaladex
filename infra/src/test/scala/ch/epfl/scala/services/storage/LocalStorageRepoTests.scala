package ch.epfl.scala.services.storage

import ch.epfl.scala.index.newModel.Project
import ch.epfl.scala.services.storage.local.LocalStorageRepo
import io.circe.parser
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class LocalStorageRepoTests() extends AsyncFunSpec with Matchers {
  describe("encode/decode json") {
    val json =
      """|
         |{
         |  "bitcoin-s/bitcoin-s":{
         |    "contributorsWanted":true,
         |    "keywords":[
         |
         |    ],
         |    "defaultArtifact":"bitcoin-s-core",
         |    "defaultStableVersion":true,
         |    "strictVersions":false,
         |    "deprecated":false,
         |    "artifactDeprecations":[
         |
         |    ],
         |    "cliArtifacts":[
         |
         |    ],
         |    "customScalaDoc":null,
         |    "documentationLinks":[
         |
         |    ],
         |    "primaryTopic":null,
         |    "beginnerIssuesLabel":"help wanted",
         |    "chatroom":null,
         |    "contributingGuide":{
         |      "target":"https://bitcoin-s.org/docs/contributing"
         |    },
         |    "codeOfConduct":null
         |  }
         |}
         |""".stripMargin

    import LocalStorageRepo._
    val dataForms = parser.decode[Map[Project.Reference, Project.DataForm]](json)
    dataForms.toTry.get.size should equal(1)
  }

}