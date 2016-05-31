package ch.epfl.scala.index
package model

import utest._

import upickle.default._

object SemanticVersionPickling extends TestSuite{
  def roundtrips(version: SemanticVersion) = 
    assert(read[SemanticVersion](write(version)) == version)

  val tests = this{
    "roundtrip"-{
      "simple"-{
        roundtrips(SemanticVersion(1, 1, Some(1), None, Some("some.meta~data")))
      }
      "empty"-{
        roundtrips(SemanticVersion(1))
      }
      "major.minor.patch"-{
       roundtrips(SemanticVersion(1, 1, Some(1)))
      }
    }
  }
}

