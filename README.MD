

[gitter.im channel]: https://gitter.im/scalacenter/scaladex
[scaladex-metadata]: https://github.com/scalacenter/scaladex-metadata
[scaladex-data]: https://github.com/scalacenter/scaladex-data
[Scaladex SBT Plugin]: /doc/plugin.md
[GitHub]: https://github.com

# Scaladex

[![Travis](https://img.shields.io/travis/scalacenter/scaladex.svg?style=flat-square)](https://travis-ci.org/scalacenter/scaladex)
[![Waffle.io](https://img.shields.io/waffle/label/scalacenter/scaladex/in%20progress.svg?style=flat-square)](https://waffle.io/scalacenter/scaladex)
[![Gitter](https://img.shields.io/gitter/room/scalacenter/scaladex.svg?style=flat-square)](https://gitter.im/scalacenter/scaladex)

## How it works

We run this process every day:

* Discovery:
  * We discover Scala release published on Maven Central as well as on Bintray. A common practice is to include the scala compiler version in the artifactId. We search for `.pom files` containing `_2.10`, `_2.11` and `_2.12`.
  * If you dont follow this convention add your releases to [non-standard.json](https://github.com/scalacenter/scaladex-data/blob/master/non-standard.json)
  * Read [`How to Publish`](/doc/how-to-publish.md) for more details.

* Download:
  * We download pom and parent poms
  * We read them, 10% are invalid (most common issue: duplicated tags see https://github.com/sbt/sbt/issues/2566)
  * We inspect the pom file for scm tag (source control management). If you forgot to add a scm tag claim your library to [claims.json](https://github.com/scalacenter/scaladex-data/blob/master/claims.json)
  * We download content and metadata from GitHub (Readme, Avatar, Descriptions, etc)


It can take up to 5 days to synchronize with Maven Central. We created an alternative way to [publish your 
metadata directly to the Scaladex](/doc/user/publish-scaladex.md).

## How to contribute

* adding keywords: submit a PR to [scaladex-metadata] or use the [Scaladex SBT Plugin]
* claiming you project: submit a PR to [scaladex-data] or use the [Scaladex SBT Plugin]
* improving the index: join the [gitter.im channel]
* read the [developper guide](/doc/dev/README.md)