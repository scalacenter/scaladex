package ch.epfl.scala.index
package data
package maven

/** Abstract model of a released artifact. Initially modeled after the POM model. Tweaked to fit with ivy.xml descriptors */
// POM Model
// https://maven.apache.org/pom.html
// javadoc: https://maven.apache.org/ref/3.3.9/maven-model/apidocs/org/apache/maven/model/Model.html
//
// the pom is defined using modello
// https://codehaus-plexus.github.io/modello/
// https://github.com/apache/maven/blob/master/maven-model/src/main/mdo/maven.mdo
case class ReleaseModel(
    groupId: String,
    artifactId: String,
    version: String,
    packaging: String,
    name: Option[String] = None,
    description: Option[String] = None,
    inceptionYear: Option[Int] = None,
    url: Option[String] = None,
    scm: Option[SourceCodeManagment] = None,
    issueManagement: Option[IssueManagement] = None,
    mailingLists: List[MailingList] = Nil,
    contributors: List[Contributor] = Nil,
    developers: List[Contributor] = Nil,
    licenses: List[License] = Nil,
    dependencies: List[Dependency] = Nil,
    repositories: List[Repository] = Nil,
    organization: Option[Organization] = None,
    sbtPluginTarget: Option[SbtPluginTarget] = None // Information on the target scala and sbt versions, in case this release describes an sbt plugin
)

case class SbtPluginTarget(scalaVersion: String, sbtVersion: String)

/*
This element describes all of the licenses for this project.
Each license is described by a <code>license</code> element, which
is then described by additional elements.
Projects should only list the license(s) that applies to the project
and not the licenses that apply to dependencies.
If multiple licenses are listed, it is assumed that the user can select
any of them, not that they must accept all.

Describes the licenses for this project. This is used to generate the license
page of the project's web site, as well as being taken into consideration in other reporting
and validation. The licenses listed for the project are that of the project itself, and not
of
 */
case class License(
    // The full legal name of the license
    name: String,
    // The official url for the licfense text.
    url: Option[String],
    /*
     * The primary method by which this project may be distributed.
     * repo: may be downloaded from the Maven repository
     * manual: user must manually download and install the dependency
     */
    distribution: Option[String] = None,
    // Addendum information pertaining to this license.
    comments: Option[String] = None
)

/** Description of a person who has contributed to the project, but who does not
    have commit privileges. Usually, these contributions come in the form of patches submitted. */
case class Contributor(
    name: Option[String],
    email: Option[String],
    url: Option[String],
    organization: Option[String],
    organizationUrl: Option[String],
    roles: List[String],
    /*
    The timezone the contributor is in. Typically, this is a number in the range
    <a href="http://en.wikipedia.org/wiki/UTC%E2%88%9212:00">-12</a> to <a href="http://en.wikipedia.org/wiki/UTC%2B14:00">+14</a>
    or a valid time zone id like "America/Montreal" (UTC-05:00) or "Europe/Paris" (UTC+01:00).
     */
    timezone: Option[String],
    // Properties about the contributor, such as an instant messenger handle.
    properties: Map[String, String],
    // Developer
    // The unique ID of the developer in the SCM.
    id: Option[String]
)

case class Dependency(
    groupId: String, // org.apache.maven
    artifactId: String, // maven-artifact
    version: String, // 3.2.1
    // url (to download if central fails)
    // type (jar, war, plugin)
    // classifier (to distinguish two artifacts)
    properties: Map[String, String] = Map(),
    scope: Option[String] = None,
    exclusions: Set[Exclusion] = Set(),
    optional: Boolean = false
) {
  override def toString = s"$groupId $artifactId $version"
}

case class Exclusion(groupId: String, artifactId: String)

case class Repository(
    id: String,
    layout: String,
    name: String,
    // The URL to the project's browsable SCM repository
    url: Option[String],
    // This connection is read-only.
    // ex: scm:git:ssh://github.com/path_to_repository
    // connection: URL,
    // This scm connection will not be read only.
    // developerConnection: URL,
    snapshotPolicies: Option[RepositoryPolicy],
    releasePolicies: Option[RepositoryPolicy]
)

case class RepositoryPolicy(
    checksumPolicy: String,
    enabled: Boolean,
    updatePolicy: String
)

// see Repository
case class SourceCodeManagment(
    connection: Option[String],
    developerConnection: Option[String],
    url: Option[String],
    tag: Option[String]
)

case class IssueManagement(
    system: String,
    url: Option[String]
)

case class MailingList(
    name: String,
    subscribe: Option[String],
    unsubscribe: Option[String],
    post: Option[String],
    archive: Option[String],
    otherArchives: List[String]
)

case class Organization(
    name: String,
    url: Option[String]
)
