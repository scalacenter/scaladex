# Scaladex

[![Travis](https://img.shields.io/travis/scalacenter/scaladex.svg?style=flat-square)](https://travis-ci.org/scalacenter/scaladex)
[![Gitter](https://img.shields.io/gitter/room/scalacenter/scaladex.svg?style=flat-square)](https://gitter.im/scalacenter/scaladex)

Scaladex is the website where all the open source Scala libraries are indexed.

## Acknowledgments

<a title="Scala Center" href="https://scala.epfl.ch/"><img alt="Scala Center" src="https://scala.epfl.ch/resources/img/scala-center-logo-black.png" height="60" /></a>

This project is funded by the <a title="Scala Center" href="https://scala.epfl.ch/">Scala Center</a>.

## How it works

We pull poms from Bintray and we receive poms from Maven Central (Sonatype).
Your library has to be published to one of those repositories.

* [`How to publish on Maven Central`](/doc/user/publish-maven-central.md)
* [`How to publish on Bintray`](/doc/user/publish-bintray.md)

You can also watch [The Last 10 Percent by Stefan Zeiger](https://www.youtube.com/watch?v=RmEMUwfQoSc).

Process:
* Releases Sources
  * Push from Maven Central (Sonatype):
    * We receive pushes from Maven Central that follow the scala artifactId convention.
      A common practice is to include the scala compiler version in the artifactId.
      The `.pom files` will contain `_2.10`, `_2.11`, `_2.12` or any further Scala Version.
  * Pull Bintray (once per day): 
    * File Search: releases that follow the scala artifactId convention presented above.
    * Non Standards: If you dont follow this convention add your releases to [non-standard.json]
    * Sbt Plugins: We index sbt plugins published to [sbt/sbt-plugin-releases] add your plugin to [claims.json]
* We download pom and parent poms
* We read them, 10% are invalid 
  (most common issue: duplicated tags see https://github.com/sbt/sbt/issues/2566)
* We inspect the pom file for scm tag (source control management). 
  If you forgot to add a scm tag claim your library or you publish a sbt plugin add claim your project to [claims.json]
* We download content and metadata from GitHub (Readme, Avatar, Descriptions, etc)

[non-standard.json]: https://github.com/scalacenter/scaladex-data/blob/master/non-standard.json
[sbt/sbt-plugin-releases]: https://bintray.com/sbt/sbt-plugin-releases
[claims.json]: https://github.com/scalacenter/scaladex-data/blob/master/claims.json
## How to contribute

Join the [gitter.im channel](https://gitter.im/scalacenter/scaladex) and 
read the [developer guide](/CONTRIBUTING.md)

## Badges API

### Show the versions of Scala supported by your project!

You can add this badge to the README.MD of your own GitHub projects to show
the versions of Scala they support:

[![Scala version Badge](https://index.scala-lang.org/typelevel/cats/cats-core/latest-by-scala-version.svg)

...the badge above only summarises latest JVM artifacts, if you'd like a badge
for  Scala JS or Scala Native, add a `targetType=...` query-string parameter:

[![Scala version Badge](https://index.scala-lang.org/typelevel/cats/cats-core/latest-by-scala-version.svg?targetType=js)

[![Scala version Badge](https://index.scala-lang.org/typelevel/cats/cats-core/latest-by-scala-version.svg?targetType=native)

### Smaller, shorter badges

[![Count Badge](https://index.scala-lang.org/count.svg?q=depends-on:typelevel/cats&subject=cats&color=orange&style=flat-square)](https://index.scala-lang.org/search?q=dependencies:typelevel/cats)

[![Latest version](https://index.scala-lang.org/typelevel/cats/cats-core/latest.svg?color=orange&style=flat-square)](https://index.scala-lang.org/typelevel/cats/cats-core)

[![Count Badge](https://index.scala-lang.org/count.svg?q=depends-on:akka/*&subject=akka&color=blue&style=flat-square&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNTggMTkwIiBlbmFibGUtYmFja2dyb3VuZD0ibmV3IDAgMCAyNTggMTkwIj48ZyBmaWxsPSIjZmZmIj48cGF0aCBvcGFjaXR5PSIuNSIgZD0iTTIxMC41IDE3Mi44YzM1LjkgMjguNyA1OC45LTU3IDEuNy03Mi44LTQ4LTEzLjMtOTYuMyA5LjUtMTQ0LjcgNjIuNy4xIDAgODkuNC0zMi43IDE0MyAxMC4xeiIvPjxwYXRoIGQ9Ik02OC40IDE2MmMzNC40LTM1LjcgOTEuNi03NS41IDE0NC45LTYwLjggMTIuNCAzLjUgMjEuMiAxMC43IDI2LjkgMTkuM2wtNTAuNC0xMDEuN2MtNy4yLTExLjUtMjUuNi05LjEtMzYtLjNsLTEzMy4yIDExMS42Yy0xMi4xIDEwLjQtMTIuOSAyOC44LTEuNiA0MC4xIDkuOSA5LjkgMjUuNiAxMC44IDM2LjUgMmwxMi45LTEwLjJ6Ii8+PC9nPjwvc3ZnPg)](https://index.scala-lang.org/search?q=dependencies:akka/*)

[![Latest version](https://index.scala-lang.org/akka/akka/akka-http-core/latest.svg?color=blue&style=flat-square&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNTggMTkwIiBlbmFibGUtYmFja2dyb3VuZD0ibmV3IDAgMCAyNTggMTkwIj48ZyBmaWxsPSIjZmZmIj48cGF0aCBvcGFjaXR5PSIuNSIgZD0iTTIxMC41IDE3Mi44YzM1LjkgMjguNyA1OC45LTU3IDEuNy03Mi44LTQ4LTEzLjMtOTYuMyA5LjUtMTQ0LjcgNjIuNy4xIDAgODkuNC0zMi43IDE0MyAxMC4xeiIvPjxwYXRoIGQ9Ik02OC40IDE2MmMzNC40LTM1LjcgOTEuNi03NS41IDE0NC45LTYwLjggMTIuNCAzLjUgMjEuMiAxMC43IDI2LjkgMTkuM2wtNTAuNC0xMDEuN2MtNy4yLTExLjUtMjUuNi05LjEtMzYtLjNsLTEzMy4yIDExMS42Yy0xMi4xIDEwLjQtMTIuOSAyOC44LTEuNiA0MC4xIDkuOSA5LjkgMjUuNiAxMC44IDM2LjUgMmwxMi45LTEwLjJ6Ii8+PC9nPjwvc3ZnPg)](https://index.scala-lang.org/akka/akka/akka-http-core)

For more information read the [shields.io API](http://shields.io/)
