# Scaladex

[![Travis](https://img.shields.io/travis/scalacenter/scaladex.svg?style=flat-square)](https://travis-ci.org/scalacenter/scaladex)
[![Gitter](https://img.shields.io/gitter/room/scalacenter/scaladex.svg?style=flat-square)](https://gitter.im/scalacenter/scaladex)

## How it works

We pull poms from Bintray and we receive poms from Maven Central (Sonatype) or to Scaladex directly.
Your library has to be published to one of those repositories.

* [`How to publish on Maven Central`](/doc/user/publish-maven-central.md)
* [`How to publish on Bintray`](/doc/user/publish-bintray.md)
* [`How to publish on Scaladex`](/doc/user/publish-scaladex.md)

Process:
* Releases Sources
  * Push from Maven Central(Sonatype):
    * We receive pushes from Maven Central that follow the artifactId convention
      presented above (pom path containing _2.11, etc).
  * Push from Scala Developers:
    * You can use the [sbt-scaladex to push releases](/doc/user/publish-scaladex.md)
    * You can also use the [publish REST api]
  * Pull Bintray (once per day): 
    * File Search: we discover Scala release based on the pom filename.
      A common practice is to include the scala compiler version in the artifactId.
      We search for `.pom files` containing `_2.10`, `_2.11` and `_2.12`.
    * Non Standards: If you dont follow this convention add your releases to [non-standard.json]
    * Sbt Plugins: We index sbt plugins published to [sbt/sbt-plugin-releases]
* We download pom and parent poms
* We read them, 10% are invalid 
  (most common issue: duplicated tags see https://github.com/sbt/sbt/issues/2566)
* We inspect the pom file for scm tag (source control management). 
  If you forgot to add a scm tag claim your library to 
  [claims.json](https://github.com/scalacenter/scaladex-data/blob/master/claims.json)
* We download content and metadata from GitHub (Readme, Avatar, Descriptions, etc)

[non-standard.json]: https://github.com/scalacenter/scaladex-data/blob/master/non-standard.json
[publish REST api]: https://github.com/scalacenter/scaladex/blob/master/server/src/main/scala/ch.epfl.scala.index.server/routes/api/PublishApi.scala
[sbt/sbt-plugin-releases]: https://bintray.com/sbt/sbt-plugin-releases

## How to contribute

Join the [gitter.im channel](https://gitter.im/scalacenter/scaladex) and 
read the [developer guide](/CONTRIBUTING.md)

## Badges API

[![Count Badge](https://index.scala-lang.org/count.svg?q=depends-on:typelevel/cats&subject=cats&color=orange&style=flat-square)](https://index.scala-lang.org/search?q=dependencies:typelevel/cats)

[![Latest version](https://index.scala-lang.org/typelevel/cats/cats-core/latest.svg?color=orange&style=flat-square)](https://index.scala-lang.org/typelevel/cats/cats-core)

[![Count Badge](https://index.scala-lang.org/count.svg?q=depends-on:akka/*&subject=akka&color=blue&style=flat-square&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNTggMTkwIiBlbmFibGUtYmFja2dyb3VuZD0ibmV3IDAgMCAyNTggMTkwIj48ZyBmaWxsPSIjZmZmIj48cGF0aCBvcGFjaXR5PSIuNSIgZD0iTTIxMC41IDE3Mi44YzM1LjkgMjguNyA1OC45LTU3IDEuNy03Mi44LTQ4LTEzLjMtOTYuMyA5LjUtMTQ0LjcgNjIuNy4xIDAgODkuNC0zMi43IDE0MyAxMC4xeiIvPjxwYXRoIGQ9Ik02OC40IDE2MmMzNC40LTM1LjcgOTEuNi03NS41IDE0NC45LTYwLjggMTIuNCAzLjUgMjEuMiAxMC43IDI2LjkgMTkuM2wtNTAuNC0xMDEuN2MtNy4yLTExLjUtMjUuNi05LjEtMzYtLjNsLTEzMy4yIDExMS42Yy0xMi4xIDEwLjQtMTIuOSAyOC44LTEuNiA0MC4xIDkuOSA5LjkgMjUuNiAxMC44IDM2LjUgMmwxMi45LTEwLjJ6Ii8+PC9nPjwvc3ZnPg)](https://index.scala-lang.org/search?q=dependencies:akka/*)

[![Latest version](https://index.scala-lang.org/akka/akka/akka-http-core/latest.svg?color=blue&style=flat-square&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNTggMTkwIiBlbmFibGUtYmFja2dyb3VuZD0ibmV3IDAgMCAyNTggMTkwIj48ZyBmaWxsPSIjZmZmIj48cGF0aCBvcGFjaXR5PSIuNSIgZD0iTTIxMC41IDE3Mi44YzM1LjkgMjguNyA1OC45LTU3IDEuNy03Mi44LTQ4LTEzLjMtOTYuMyA5LjUtMTQ0LjcgNjIuNy4xIDAgODkuNC0zMi43IDE0MyAxMC4xeiIvPjxwYXRoIGQ9Ik02OC40IDE2MmMzNC40LTM1LjcgOTEuNi03NS41IDE0NC45LTYwLjggMTIuNCAzLjUgMjEuMiAxMC43IDI2LjkgMTkuM2wtNTAuNC0xMDEuN2MtNy4yLTExLjUtMjUuNi05LjEtMzYtLjNsLTEzMy4yIDExMS42Yy0xMi4xIDEwLjQtMTIuOSAyOC44LTEuNiA0MC4xIDkuOSA5LjkgMjUuNiAxMC44IDM2LjUgMmwxMi45LTEwLjJ6Ii8+PC9nPjwvc3ZnPg)](https://index.scala-lang.org/akka/akka/akka-http-core)

For more information read the [shields.io API](http://shields.io/)
