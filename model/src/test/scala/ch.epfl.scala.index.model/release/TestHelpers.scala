package ch.epfl.scala.index.model
package release

trait TestHelpers {
  def assert2(a: String, b: String): Unit = {
    val ok = a == b
    if (!ok) {
      println()
      println("---")
      println(a + "|")
      println(b + "|")
      assert(a == b)
    }
  }

  def release(groupId: String,
              artifactId: String,
              version: String,
              artifactName: String,
              target: Option[ScalaTarget],
              isNonStandardLib: Boolean = false,
              resolver: Option[Resolver] = None) = {
    Release(
      maven = MavenReference(
        groupId = groupId,
        artifactId = artifactId,
        version = version
      ),
      reference = Release.Reference(
        artifact = artifactName,
        version = SemanticVersion(version).get,
        target = target,
        // Not necessary for the test
        organization = "GitHub-Org",
        repository = "GitHub-Repo"
      ),
      resolver = resolver,
      isNonStandardLib = isNonStandardLib,
      // default/elasticsearch fields
      name = None,
      description = None,
      released = None,
      licenses = Set(),
      id = None,
      liveData = false,
      scalaDependencies = Seq(),
      javaDependencies = Seq(),
      reverseDependencies = Seq(),
      internalDependencies = Seq(),
      targetType = "",
      fullScalaVersion = None,
      scalaVersion = None,
      scalaJsVersion = None,
      scalaNativeVersion = None,
      sbtVersion = None
    )
  }
}
