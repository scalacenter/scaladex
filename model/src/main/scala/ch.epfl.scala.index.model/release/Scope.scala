package ch.epfl.scala.index.model.release

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

/**
 * Scope trait
 */
sealed trait Scope {
  def name: String
}