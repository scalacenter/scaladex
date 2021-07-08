package ch.epfl.scala.index.model
package release

/**
 * Reference to a maven artifact
 * - com.typesafe.akka - akka-http-experimental_2.11 - 2.4.6
 * - org.typelevel - cats-core_sjs0.6_2.11 - 0.6.0
 *
 * @param groupId the reference group id ex: org.typelevel
 * @param artifactId the reference artifact name ex: cats-core_sjs0.6_2.11
 * @param version the release version which is references ex: 0.6.0
 */
case class MavenReference(
    groupId: String,
    artifactId: String,
    version: String
) extends GeneralReference {

  /**
   * name accessor
   * @return
   */
  def name: String = s"$groupId/$artifactId"

  /**
   * url to maven page with related information to this reference
   * @return
   */
  def httpUrl: String =
    s"http://search.maven.org/#artifactdetails|$groupId|$artifactId|$version|jar"
}
