package ch.epfl.scala.index
package data
package github

/*
organization avatar_url <=> Logo
*/

case class User(
  login: String,
  avatar_url: String // https://avatars.githubusercontent.com/u/3731824?v=3
)

case class Repository(
  name: String, // cats
  owner: User,
  `private`: Boolean,
  description: String,
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
  subscribers_count: Int // Watch
)