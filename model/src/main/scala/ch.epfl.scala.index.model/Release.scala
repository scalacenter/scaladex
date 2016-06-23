package ch.epfl.scala.index.model

// typelevel/cats-core (scalajs 0.6, scala 2.11) 0.6.0
case class Release(
  // famous maven triple: org.typelevel - cats-core_sjs0.6_2.11 - 0.6.0
  maven: MavenReference,
  // similar to maven but with a clean artifact name
  reference: Release.Reference,
  // human readable name (ex: Apache Spark)
  name: Option[String] = None,
  description: Option[String] = None,
  // potentially various dates because bintray allows republishing
  releaseDates: List[ISO_8601_Date] = Nil,
  // availability on the central repository
  mavenCentral: Boolean = false,
  licenses: Set[License] = Set(),

  /** split dependencies in 2 fields because elastic can't handle 2 different types
    * in one field. That is a simple workaround for that
    */
  scalaDependencies: Seq[ScalaDependency] = Seq(),
  javaDependencies: Seq[JavaDependency] = Seq(),
  reverseDependencies: Seq[ScalaDependency] = Seq()
) {
  def sbtInstall = {
    val scalaJs = reference.targets.scalaJsVersion.isDefined
    val crossFull = reference.targets.scalaVersion.patch.isDefined
  
    val (artifactOperator, crossSuffix) =
      if (scalaJs)       ("%%%",                         "")
      else if(crossFull) (  "%", " cross CrossVersion.full")
      else               ( "%%",                         "")

    s""""${maven.groupId}" $artifactOperator "${reference.artifact}" % "${reference.version}$crossSuffix""""
  }
  def mavenInstall = {
    import maven._
    s"""|<dependency>
        |  <groupId>$groupId</groupId>
        |  <artifactId>$artifactId</artifactId>
        |  <version>$version</version>
        |</dependency>""".stripMargin
  }
  def gradleInstall = {
    import maven._
    s"compile group: '$groupId', name: '$artifactId', version: '$version'"
  }
  def scalaDocURI: Option[String] = {
    if(mavenCentral) {
      import maven._
      // no frame
      // hosted on s3 at:
      // https://static.javadoc.io/$groupId/$artifactId/$version/index.html#package
      // HEAD to check 403 vs 200

      Some(s"https://www.javadoc.io/doc/$groupId/$artifactId/$version")
    } else None
  }

  lazy val orderedDependencies = scalaDependencies.sortBy(_.scope.contains(Scope.Test))
  lazy val orderedJavaDependencies = javaDependencies.sortBy(_.scope.contains(Scope.Test))
  lazy val orderedReverseDependencies = reverseDependencies.sortBy(_.scope.contains(Scope.Test))

  /** collect all unique organization/artifact dependency */
  lazy val uniqueOrderedReverseDependencies = {

    orderedReverseDependencies.foldLeft(Seq[ScalaDependency]()) { (current, next) =>

      if (current.exists(_.dependency.name == next.dependency.name)) current else current :+ next
    }
  }

  lazy val dependencyCount = scalaDependencies.size + javaDependencies.size

  /**
    * collect a list of version for a reverse dependency
    * @param dep current looking dependency
    * @return
    */
  def versionsForReverseDependencies(dep: ScalaDependency): Seq[SemanticVersion] =  {

    val deps = reverseDependencies.filter(d => d.dependency.name == dep.dependency.name)

    println(deps)
    deps.map(_.dependency.version)
  }
}

/**
  * General Reference to Group MavenReference and Release.Reference
  * to a category form simpler usage.
  */
sealed trait GeneralReference {

  def name: String
  def httpUrl: String
}

object Release{
  case class Reference(
    organization: String, // typelevel               | akka
    artifact: String, // cats-core               | akka-http-experimental
    version: SemanticVersion, // 0.6.0                   | 2.4.6
    targets: ScalaTargets // scalajs 0.6, scala 2.11 | scala 2.11
  ) extends GeneralReference {

    def name: String = s"$organization/$artifact"
    def httpUrl: String = s"/$organization/$artifact/$version"
  }
}

// com.typesafe.akka - akka-http-experimental_2.11 - 2.4.6 | org.typelevel - cats-core_sjs0.6_2.11 - 0.6.0
case class MavenReference(
   groupId: String, // org.typelevel         | com.typesafe.akka
   artifactId: String, // cats-core_sjs0.6_2.11 | akka-http-experimental_2.11
   version: String // 0.6.0                 | 2.4.6
) extends GeneralReference {

  def name: String = s"$groupId/$artifactId"
  def httpUrl: String = s"http://search.maven.org/#artifactdetails|$groupId|$artifactId|$version|jar"
}

case class ScalaTargets(scalaVersion: SemanticVersion, scalaJsVersion: Option[SemanticVersion] = None) {

  /** simple modifier for display a nice name */
  lazy val name = scalaJsVersion.map(v => s"Scala.js ${v.toString} ($scalaVersion)").getOrElse(s"Scala $scalaVersion")

  /** simple modifier for ordering */
  lazy val orderName: String = scalaJsVersion.map(v => s"${scalaVersion.toString.replace(".", "")}_${v.toString.replace(".", "")}").getOrElse(scalaVersion.toString.replace(".", ""))
}

case class ISO_8601_Date(value: String) // 2016-05-20T12:48:52.533-04:00

sealed trait Scope {
  def name: String
}
object Scope {

  /** Sbt Scopes: http://www.scala-sbt.org/0.12.2/docs/Getting-Started/Scopes.html
    * Maven Scopes: http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope
    */
  case object Test extends Scope {
    val name = "test"
  }
  case object Provided extends Scope {
    val name = "provided"
  }
  case object Compile extends Scope {
    val name = "compile"
  }
  case object Runtime extends Scope {
    val name = "runtime"
  }
  case object Optional extends Scope {
    val name = "optional"
  }
  case object System extends Scope {
    val name = "system"
  }
  case object Import extends Scope {
    val name = "import"
  }

  /**
    * Convert String scope to Scope class
 *
    * @param scope the current scope
    * @return
    */
  def apply(scope: String): Scope = scope.toLowerCase match {

    case Test.name => Test
    case Provided.name => Provided
    case Compile.name => Compile
    case Runtime.name => Runtime
    case Optional.name => Optional
    case System.name => System
    case Import.name => Import

    case unknown =>
      throw new RuntimeException(s"unknown scope $unknown detected during pom parsing.")
  }
}

sealed trait Dependency {

  def dependency: GeneralReference
  def scope: Option[Scope]
}

case class ScalaDependency(dependency: Release.Reference, scope: Option[Scope]) extends Dependency
case class JavaDependency(dependency: MavenReference, scope: Option[Scope]) extends Dependency
