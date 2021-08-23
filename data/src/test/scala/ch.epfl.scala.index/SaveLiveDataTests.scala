package ch.epfl.scala.index

import ch.epfl.scala.index.data.elastic.LiveProjects
import ch.epfl.scala.index.data.elastic.SaveLiveData
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers

class SaveLiveDataTests extends AsyncFunSpec with Matchers {
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
         |    "beginnerIssues":[
         |      {
         |        "number":405,
         |        "title":"Have `start()` method on BitcoindRpcClient return the started client rather than Unit",
         |        "url":{
         |          "target":"https://github.com/bitcoin-s/bitcoin-s/issues/405"
         |        }
         |      }
         |    ],
         |    "selectedBeginnerIssues":[
         |
         |    ],
         |    "chatroom":null,
         |    "contributingGuide":{
         |      "target":"https://bitcoin-s.org/docs/contributing"
         |    },
         |    "codeOfConduct":null
         |  }
         |}
         |""".stripMargin

    val readLiveProject: LiveProjects = SaveLiveData.parse(json)
    readLiveProject.projects.size should equal(1)
  }
}
