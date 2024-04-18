# Scaladex

![CI](https://github.com/scalacenter/scaladex/actions/workflows/ci.yml/badge.svg)
![GitHub Discussions](https://img.shields.io/github/discussions/scalacenter/scaladex)

Scaladex is the website where the open source Scala libraries are indexed.
Its main purpose is to help Scala developers find useful libraries and to help library authors promote their libraries and find new contributors.

## Acknowledgments

<picture>
    <source media="(prefers-color-scheme: light)" srcset="https://scala.epfl.ch/resources/img/scala-center-logo-black.png">
    <source media="(prefers-color-scheme: dark)" srcset="https://scala.epfl.ch/resources/img/scala-center-logo.png">
    <img alt="Scala Center" src="https://scala.epfl.ch/resources/img/scala-center-logo.png" height="60">
</picture>

This project is funded by the <a title="Scala Center" href="https://scala.epfl.ch/">Scala Center</a>.

## How it works

Scaladex receives poms automatically from Maven Central (Sonatype) based on the binary version of the artifact ID.
Some valid Scala binary versions are `_2.13`, `_3`, `_sjs1_3`, `_native0.4_2.13`, `_2.12_1.0`.

Scaladex associates a new artifact to a project by looking at the `scm` (Source Code Management) attribute in the pom file.
At the moment Scaladex only supports Github repositories.

The description of a project (its readme, its avatar, its website link...) are automatically downloaded from Github.

## Troubleshooting

### My project is missing or some artifacts are missing

#### Did you publish the artifacts to Maven Central?

Check out how to publish to Maven Central with sbt or Mill:
- [Publish with sbt](https://www.scala-sbt.org/1.x/docs/Publishing.html)
- [Automated publish with sbt](https://github.com/sbt/sbt-ci-release)
- [Publish with Mill](https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html#_deploying_your_code)

You can also watch [The Last 10 Percent by Stefan Zeiger](https://www.youtube.com/watch?v=RmEMUwfQoSc).

#### What is the binary version of your artifacts?

If your artifact does not have any binary version it is considered a Java artifact and it will not be automatically indexed.
Yet some Java artifact are closely related to Scala.
In that case you can force its indexing by updating the [non-standard.json](https://github.com/scalacenter/scaladex-contrib/blob/master/non-standard.json) file in the [scaladex-contrib](https://github.com/scalacenter/scaladex-contrib) repository.

At the moment we don't support full Scala binary versions, that are often used in Scala compiler plugins.

#### Does the pom file contain the `scm` attribute and does it points to a public Github repository?

If not you can claim that the artifact belongs to your Github repository by updating the [claims.json](https://github.com/scalacenter/scaladex-contrib/blob/master/claims.json) file in the [scaladex-contrib](https://github.com/scalacenter/scaladex-contrib) repository.

If your project is not hosted by Github you should consider creating a mirror of it in Github.

Do not forget to update the `scmInfo` setting in your build file before the next release.

### My project is hard to find in the search page

Read [How to improve the visibility of your project](doc/user/improve-visibility.md).

## How to contribute

Read the [Contributing Guide](/CONTRIBUTING.md) and use [Github Discussions](https://github.com/scalacenter/scaladex/discussions) for doubts.

## Badges

### Show the versions of Scala supported by your project!

You can add this badge to the README.MD of your own GitHub projects to show
the versions of Scala they support:

[![cats-core Scala version support](https://index.scala-lang.org/typelevel/cats/cats-core/latest-by-scala-version.svg)](https://index.scala-lang.org/typelevel/cats/cats-core)

The badge above only summarises latest JVM artifacts, if you'd like a badge
for  Scala JS or Scala Native, add a `targetType=...` query-string parameter:

[![cats-core Scala version support](https://index.scala-lang.org/typelevel/cats/cats-core/latest-by-scala-version.svg?targetType=js)](https://index.scala-lang.org/typelevel/cats/cats-core)

[![cats-core Scala version support](https://index.scala-lang.org/typelevel/cats/cats-core/latest-by-scala-version.svg?targetType=native)](https://index.scala-lang.org/typelevel/cats/cats-core)

### Smaller, shorter badges

[![Latest version](https://index.scala-lang.org/typelevel/cats/cats-core/latest.svg?color=orange)](https://index.scala-lang.org/typelevel/cats/cats-core)

[![Latest version](https://index.scala-lang.org/akka/akka/akka-http-core/latest.svg?color=blue)](https://index.scala-lang.org/akka/akka/akka-http-core)

For more information read the [shields.io API](http://shields.io/)
