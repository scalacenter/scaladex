package ch.epfl.scala.index
package data
package github

// TODO: should be refactored to clean classes
/*
organization avatar_url <=> Logo
 */

case class GithubCredentials(token: String)

case class User(
    login: String,
    avatar_url: String // https://avatars.githubusercontent.com/u/3731824?v=3
)

case class Repository(
    name: String, // cat
    owner: User,
    `private`: Boolean,
    description: Option[String],
    fork: Boolean,
    created_at: String, // format: "2015-01-28T20:26:48Z",
    updated_at: String,
    homepage: Option[String], // http://typelevel.org/cats/
    size: Int, // 11602 repo size in Kb
    stargazers_count: Int, // stars
    // language: Option[String], // "Scala"
    // has_issues: Boolean,
    // has_downloads: Boolean,
    // has_wiki: Boolean,
    // has_pages: Boolean,
    forks_count: Int,
    mirror_url: Option[String], // "mirror_url": "git://git.apache.org/spark.git",
    open_issues_count: Int,
    forks: Int,
    open_issues: Int,
    default_branch: String, // master
    organization: Option[User],
    subscribers_count: Int, // Watch
    permissions: Option[Permissions]
)

case class Permissions(
    admin: Boolean,
    push: Boolean,
    pull: Boolean
)

case class Contributor(
    login: String,
    id: Int,
    avatar_url: String,
    gravatar_id: String,
    url: String,
    html_url: String,
    followers_url: String,
    following_url: String,
    gists_url: String,
    starred_url: String,
    subscriptions_url: String,
    organizations_url: String,
    repos_url: String,
    events_url: String,
    received_events_url: String,
    `type`: String,
    site_admin: Boolean,
    contributions: Int
)

case class CommunityProfile(files: CommunityFiles)

case class CommunityFiles(contributing: ContributingFile)

case class ContributingFile(html_url: Option[String])

// classes for GraphQL API, https://developer.github.com/v4/reference/
// note that some classes are missing members, only got ones needed for topics

case class GraphqlTopic(
    name: Option[String],
    relatedTopics: Option[List[GraphqlTopic]]
)

case class GraphqlRepositoryTopic(
    resourcePath: Option[String],
    topic: Option[GraphqlTopic],
    url: Option[String]
)

case class GraphqlIssue(
    number: Option[Int],
    title: Option[String],
    bodyText: Option[String],
    url: Option[String]
)

case class GraphqlRepositoryTopicConnection(
    nodes: Option[List[GraphqlRepositoryTopic]]
)

case class GraphqlIssueConnection(
    nodes: Option[List[GraphqlIssue]]
)

case class GraphqlRepository(
    name: Option[String],
    description: Option[String],
    repositoryTopics: Option[GraphqlRepositoryTopicConnection],
    issues: Option[GraphqlIssueConnection]
)

// if you want to query starting with anything other than repository, you will have to add it as a member here
case class GraphqlData(
    repository: Option[GraphqlRepository]
)

case class GraphqlResult(
    data: Option[GraphqlData]
)
